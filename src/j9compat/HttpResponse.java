package j9compat;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;

/**
 * Minimal Java 8-compatible backport of {@code java.net.http.HttpResponse}.
 */
public interface HttpResponse<T> {

    int statusCode();

    HttpRequest request();

    Optional<HttpResponse<T>> previousResponse();

    HttpHeaders headers();

    T body();

    Optional<SSLSession> sslSession();

    URI uri();

    HttpClient.Version version();

    interface ResponseInfo {
        int statusCode();
        HttpHeaders headers();
        HttpClient.Version version();
    }

    interface BodyHandler<T> {
        BodySubscriber<T> apply(ResponseInfo responseInfo);
    }

    interface BodySubscriber<T> extends Flow.Subscriber<List<ByteBuffer>> {
        CompletionStage<T> getBody();
    }

    interface PushPromiseHandler<T> {
        void applyPushPromise(HttpRequest initiatingRequest,
                              HttpRequest pushPromiseRequest,
                              Function<BodyHandler<T>, CompletableFuture<HttpResponse<T>>> acceptor);
    }

    final class BodyHandlers {

        private BodyHandlers() {}

        public static BodyHandler<String> ofString() {
            return ofString(StandardCharsets.UTF_8);
        }

        public static BodyHandler<String> ofString(Charset charset) {
            if (charset == null) {
                throw new NullPointerException("charset");
            }
            return new StringBodyHandler(charset);
        }

        public static BodyHandler<byte[]> ofByteArray() {
            return new ByteArrayBodyHandler();
        }

        public static BodyHandler<InputStream> ofInputStream() {
            return new InputStreamBodyHandler();
        }

        public static BodyHandler<Path> ofFile(Path path) {
            return ofFile(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }

        public static BodyHandler<Path> ofFile(Path path, OpenOption... options) {
            if (path == null) {
                throw new NullPointerException("path");
            }
            return new FileBodyHandler(path, options);
        }

        public static BodyHandler<Stream<String>> ofLines() {
            return ofLines(StandardCharsets.UTF_8);
        }

        public static BodyHandler<Stream<String>> ofLines(Charset charset) {
            if (charset == null) {
                throw new NullPointerException("charset");
            }
            return new LinesBodyHandler(charset);
        }

        public static BodyHandler<Void> discarding() {
            return new DiscardingBodyHandler();
        }

        private static final class StringBodyHandler implements BodyHandler<String> {
            private final Charset charset;

            private StringBodyHandler(Charset charset) {
                this.charset = charset;
            }

            @Override
            public BodySubscriber<String> apply(ResponseInfo responseInfo) {
                return new ByteArraySubscriber<String>(charset) {
                    @Override
                    String convert(byte[] bytes, Charset charset) {
                        return new String(bytes, charset);
                    }
                };
            }
        }

        private static final class ByteArrayBodyHandler implements BodyHandler<byte[]> {
            @Override
            public BodySubscriber<byte[]> apply(ResponseInfo responseInfo) {
                return new ByteArraySubscriber<byte[]>(null) {
                    @Override
                    byte[] convert(byte[] bytes, Charset charset) {
                        return bytes;
                    }
                };
            }
        }

        private static final class InputStreamBodyHandler implements BodyHandler<InputStream> {
            @Override
            public BodySubscriber<InputStream> apply(ResponseInfo responseInfo) {
                return new StreamingInputStreamSubscriber();
            }
        }

        private static final class FileBodyHandler implements BodyHandler<Path> {
            private final Path path;
            private final OpenOption[] options;

            private FileBodyHandler(Path path, OpenOption[] options) {
                this.path = path;
                this.options = options == null ? new OpenOption[0] : options.clone();
            }

            @Override
            public BodySubscriber<Path> apply(ResponseInfo responseInfo) {
                return new FileBodySubscriber(path, options);
            }
        }

        private static final class LinesBodyHandler implements BodyHandler<Stream<String>> {
            private final Charset charset;

            private LinesBodyHandler(Charset charset) {
                this.charset = charset;
            }

            @Override
            public BodySubscriber<Stream<String>> apply(ResponseInfo responseInfo) {
                return new LinesBodySubscriber(charset);
            }
        }

        private static final class DiscardingBodyHandler implements BodyHandler<Void> {
            @Override
            public BodySubscriber<Void> apply(ResponseInfo responseInfo) {
                return new DiscardingBodySubscriber();
            }
        }

        private abstract static class ByteArraySubscriber<T> implements BodySubscriber<T> {
            private final Charset charset;
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            private final CompletableFuture<T> future = new CompletableFuture<T>();

            private ByteArraySubscriber(Charset charset) {
                this.charset = charset;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (subscription != null) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                if (items == null) {
                    return;
                }
                for (ByteBuffer item : items) {
                    if (item == null) {
                        continue;
                    }
                    ByteBuffer copy = item.slice();
                    byte[] chunk = new byte[copy.remaining()];
                    copy.get(chunk);
                    buffer.write(chunk, 0, chunk.length);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(convert(buffer.toByteArray(), charset));
            }

            @Override
            public CompletionStage<T> getBody() {
                return future;
            }

            abstract T convert(byte[] bytes, Charset charset);
        }

        private static final class StreamingInputStreamSubscriber implements BodySubscriber<InputStream> {
            private final CompletableFuture<InputStream> future = new CompletableFuture<InputStream>();
            private final StreamingPipe pipe = new StreamingPipe();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                future.complete(pipe.inputStream());
                if (subscription != null) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                if (items == null) {
                    return;
                }
                for (ByteBuffer item : items) {
                    if (item == null) {
                        continue;
                    }
                    ByteBuffer copy = item.slice();
                    byte[] chunk = new byte[copy.remaining()];
                    copy.get(chunk);
                    pipe.write(chunk, 0, chunk.length);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                pipe.closeWithError(throwable);
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                pipe.close();
            }

            @Override
            public CompletionStage<InputStream> getBody() {
                return future;
            }
        }

        private static final class LinesBodySubscriber implements BodySubscriber<Stream<String>> {
            private final Charset charset;
            private final StreamingInputStreamSubscriber delegate;
            private final CompletableFuture<Stream<String>> future = new CompletableFuture<Stream<String>>();

            private LinesBodySubscriber(Charset charset) {
                this.charset = charset;
                this.delegate = new StreamingInputStreamSubscriber();
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                delegate.onSubscribe(subscription);
                InputStream input = await(delegate.getBody());
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, charset));
                Stream<String> stream = reader.lines().onClose(new CloseableAction(reader));
                future.complete(stream);
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                delegate.onNext(items);
            }

            @Override
            public void onError(Throwable throwable) {
                delegate.onError(throwable);
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                delegate.onComplete();
            }

            @Override
            public CompletionStage<Stream<String>> getBody() {
                return future;
            }

            private InputStream await(CompletionStage<InputStream> stage) {
                try {
                    return stage.toCompletableFuture().get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        }

        private static final class FileBodySubscriber implements BodySubscriber<Path> {
            private final Path path;
            private final OpenOption[] options;
            private final CompletableFuture<Path> future = new CompletableFuture<Path>();
            private OutputStream out;

            private FileBodySubscriber(Path path, OpenOption[] options) {
                this.path = path;
                this.options = options == null ? new OpenOption[0] : options.clone();
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                try {
                    out = Files.newOutputStream(path, options);
                } catch (IOException e) {
                    future.completeExceptionally(e);
                    if (subscription != null) {
                        subscription.cancel();
                    }
                    return;
                }
                if (subscription != null) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                if (out == null || items == null) {
                    return;
                }
                try {
                    for (ByteBuffer item : items) {
                        if (item == null) {
                            continue;
                        }
                        ByteBuffer copy = item.slice();
                        byte[] chunk = new byte[copy.remaining()];
                        copy.get(chunk);
                        out.write(chunk);
                    }
                } catch (IOException e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                closeQuietly(out);
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                closeQuietly(out);
                future.complete(path);
            }

            @Override
            public CompletionStage<Path> getBody() {
                return future;
            }
        }

        private static final class DiscardingBodySubscriber implements BodySubscriber<Void> {
            private final CompletableFuture<Void> future = new CompletableFuture<Void>();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                if (subscription != null) {
                    subscription.request(Long.MAX_VALUE);
                }
            }

            @Override
            public void onNext(List<ByteBuffer> items) {
                // discard
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }

            @Override
            public CompletionStage<Void> getBody() {
                return future;
            }
        }

        private static final class StreamingPipe {
            private static final int PIPE_BUFFER_SIZE = 65536;
            private final java.io.PipedInputStream input;
            private final java.io.PipedOutputStream output;

            private StreamingPipe() {
                try {
                    this.input = new java.io.PipedInputStream(PIPE_BUFFER_SIZE);
                    this.output = new java.io.PipedOutputStream(input);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }

            InputStream inputStream() {
                return input;
            }

            void write(byte[] data, int offset, int length) {
                try {
                    output.write(data, offset, length);
                } catch (IOException e) {
                    closeWithError(e);
                }
            }

            void close() {
                closeQuietly(output);
            }

            void closeWithError(Throwable throwable) {
                closeQuietly(output);
            }
        }

        private static final class CloseableAction implements Runnable {
            private final Closeable closeable;

            private CloseableAction(Closeable closeable) {
                this.closeable = closeable;
            }

            @Override
            public void run() {
                closeQuietly(closeable);
            }
        }

        private static void closeQuietly(Closeable closeable) {
            if (closeable == null) {
                return;
            }
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
