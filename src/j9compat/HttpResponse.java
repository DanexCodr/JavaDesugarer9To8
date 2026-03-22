package j9compat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
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
                return new ByteArraySubscriber<byte[]>(StandardCharsets.UTF_8) {
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
                return new ByteArraySubscriber<InputStream>(StandardCharsets.UTF_8) {
                    @Override
                    InputStream convert(byte[] bytes, Charset charset) {
                        return new ByteArrayInputStream(bytes);
                    }
                };
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
    }
}
