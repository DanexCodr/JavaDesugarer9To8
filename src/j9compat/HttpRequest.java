package j9compat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Minimal Java 8-compatible backport of {@code java.net.http.HttpRequest}.
 */
public class HttpRequest {

    private final URI uri;
    private final String method;
    private final HttpHeaders headers;
    private final BodyPublisher bodyPublisher;
    private final Optional<Duration> timeout;
    private final Optional<HttpClient.Version> version;
    private final boolean expectContinue;

    private HttpRequest(URI uri,
                        String method,
                        HttpHeaders headers,
                        BodyPublisher bodyPublisher,
                        Duration timeout,
                        HttpClient.Version version,
                        boolean expectContinue) {
        this.uri = uri;
        this.method = method;
        this.headers = headers;
        this.bodyPublisher = bodyPublisher;
        this.timeout = Optional.ofNullable(timeout);
        this.version = Optional.ofNullable(version);
        this.expectContinue = expectContinue;
    }

    public static Builder newBuilder() {
        return new BuilderImpl();
    }

    public static Builder newBuilder(URI uri) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        return new BuilderImpl().uri(uri);
    }

    public URI uri() {
        return uri;
    }

    public String method() {
        return method;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public Optional<BodyPublisher> bodyPublisher() {
        return Optional.ofNullable(bodyPublisher);
    }

    public Optional<Duration> timeout() {
        return timeout;
    }

    public boolean expectContinue() {
        return expectContinue;
    }

    public Optional<HttpClient.Version> version() {
        return version;
    }

    public interface BodyPublisher extends Flow.Publisher<ByteBuffer> {
        long contentLength();
    }

    public interface Builder {
        Builder uri(URI uri);
        Builder header(String name, String value);
        Builder headers(String... headers);
        Builder timeout(Duration duration);
        Builder expectContinue(boolean enable);
        Builder version(HttpClient.Version version);
        Builder GET();
        Builder POST(BodyPublisher bodyPublisher);
        Builder PUT(BodyPublisher bodyPublisher);
        Builder DELETE();
        Builder method(String name, BodyPublisher bodyPublisher);
        HttpRequest build();
    }

    public static final class BodyPublishers {

        private BodyPublishers() {}

        public static BodyPublisher noBody() {
            return new ByteArrayBodyPublisher(new byte[0]);
        }

        public static BodyPublisher ofString(String body) {
            return ofString(body, StandardCharsets.UTF_8);
        }

        public static BodyPublisher ofString(String body, Charset charset) {
            if (body == null) {
                throw new NullPointerException("body");
            }
            if (charset == null) {
                throw new NullPointerException("charset");
            }
            return new ByteArrayBodyPublisher(body.getBytes(charset));
        }

        public static BodyPublisher ofByteArray(byte[] bytes) {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            return new ByteArrayBodyPublisher(Arrays.copyOf(bytes, bytes.length));
        }

        public static BodyPublisher ofByteArray(byte[] bytes, int offset, int length) {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            if (offset < 0 || length < 0 || offset + length > bytes.length) {
                throw new IndexOutOfBoundsException("offset=" + offset + ", length=" + length);
            }
            byte[] copy = new byte[length];
            System.arraycopy(bytes, offset, copy, 0, length);
            return new ByteArrayBodyPublisher(copy);
        }

        public static BodyPublisher ofInputStream(Supplier<? extends InputStream> supplier) {
            if (supplier == null) {
                throw new NullPointerException("supplier");
            }
            return new InputStreamBodyPublisher(supplier);
        }

        public static BodyPublisher ofFile(Path path) {
            if (path == null) {
                throw new NullPointerException("path");
            }
            return new FileBodyPublisher(path);
        }

        public static BodyPublisher fromPublisher(Flow.Publisher<ByteBuffer> publisher) {
            return fromPublisher(publisher, -1);
        }

        public static BodyPublisher fromPublisher(Flow.Publisher<ByteBuffer> publisher, long contentLength) {
            if (publisher == null) {
                throw new NullPointerException("publisher");
            }
            return new PublisherBodyPublisher(publisher, contentLength);
        }
    }

    static final class ByteArrayBodyPublisher implements BodyPublisher {
        private final byte[] bytes;

        ByteArrayBodyPublisher(byte[] bytes) {
            this.bytes = bytes;
        }

        byte[] bytes() {
            return bytes;
        }

        @Override
        public long contentLength() {
            return bytes.length;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("subscriber");
            }
            subscriber.onSubscribe(NoOpSubscription.INSTANCE);
            if (bytes.length > 0) {
                subscriber.onNext(ByteBuffer.wrap(bytes));
            }
            subscriber.onComplete();
        }
    }

    static final class InputStreamBodyPublisher implements BodyPublisher {
        private final Supplier<? extends InputStream> supplier;

        InputStreamBodyPublisher(Supplier<? extends InputStream> supplier) {
            this.supplier = supplier;
        }

        Supplier<? extends InputStream> supplier() {
            return supplier;
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("subscriber");
            }
            subscriber.onSubscribe(new StreamSubscription(subscriber, supplier));
        }
    }

    static final class FileBodyPublisher implements BodyPublisher {
        private final Path path;

        FileBodyPublisher(Path path) {
            this.path = path;
        }

        Path path() {
            return path;
        }

        @Override
        public long contentLength() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (subscriber == null) {
                throw new NullPointerException("subscriber");
            }
            subscriber.onSubscribe(new StreamSubscription(subscriber, new Supplier<InputStream>() {
                @Override
                public InputStream get() {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }));
        }
    }

    static final class PublisherBodyPublisher implements BodyPublisher {
        private final Flow.Publisher<ByteBuffer> publisher;
        private final long contentLength;

        PublisherBodyPublisher(Flow.Publisher<ByteBuffer> publisher, long contentLength) {
            this.publisher = publisher;
            this.contentLength = contentLength;
        }

        Flow.Publisher<ByteBuffer> publisher() {
            return publisher;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            publisher.subscribe(subscriber);
        }
    }

    private static final class BuilderImpl implements Builder {
        private URI uri;
        private String method;
        private BodyPublisher bodyPublisher;
        private final Map<String, List<String>> headers = new LinkedHashMap<String, List<String>>();
        private Duration timeout;
        private HttpClient.Version version;
        private boolean expectContinue;

        @Override
        public Builder uri(URI uri) {
            if (uri == null) {
                throw new NullPointerException("uri");
            }
            this.uri = uri;
            return this;
        }

        @Override
        public Builder header(String name, String value) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (value == null) {
                throw new NullPointerException("value");
            }
            List<String> list = headers.get(name);
            if (list == null) {
                list = new ArrayList<String>();
                headers.put(name, list);
            }
            list.add(value);
            return this;
        }

        @Override
        public Builder headers(String... headers) {
            if (headers == null) {
                throw new NullPointerException("headers");
            }
            if (headers.length % 2 != 0) {
                throw new IllegalArgumentException("headers must be name/value pairs");
            }
            for (int i = 0; i < headers.length; i += 2) {
                header(headers[i], headers[i + 1]);
            }
            return this;
        }

        @Override
        public Builder timeout(Duration duration) {
            if (duration == null) {
                throw new NullPointerException("duration");
            }
            this.timeout = duration;
            return this;
        }

        @Override
        public Builder expectContinue(boolean enable) {
            this.expectContinue = enable;
            return this;
        }

        @Override
        public Builder version(HttpClient.Version version) {
            if (version == null) {
                throw new NullPointerException("version");
            }
            this.version = version;
            return this;
        }

        @Override
        public Builder GET() {
            return method("GET", BodyPublishers.noBody());
        }

        @Override
        public Builder POST(BodyPublisher bodyPublisher) {
            return method("POST", bodyPublisher);
        }

        @Override
        public Builder PUT(BodyPublisher bodyPublisher) {
            return method("PUT", bodyPublisher);
        }

        @Override
        public Builder DELETE() {
            return method("DELETE", BodyPublishers.noBody());
        }

        @Override
        public Builder method(String name, BodyPublisher bodyPublisher) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.method = name;
            this.bodyPublisher = bodyPublisher;
            return this;
        }

        @Override
        public HttpRequest build() {
            if (uri == null) {
                throw new IllegalStateException("uri not set");
            }
            String resolvedMethod = method == null ? "GET" : method;
            BodyPublisher resolvedBody = bodyPublisher;
            if (resolvedBody == null) {
                resolvedBody = BodyPublishers.noBody();
            }
            Map<String, List<String>> frozen = new LinkedHashMap<String, List<String>>();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                frozen.put(entry.getKey(),
                        Collections.unmodifiableList(new ArrayList<String>(entry.getValue())));
            }
            HttpHeaders httpHeaders = HttpHeaders.of(frozen, new AcceptAllFilter());
            return new HttpRequest(uri, resolvedMethod, httpHeaders, resolvedBody,
                    timeout, version, expectContinue);
        }
    }

    private static final class AcceptAllFilter implements java.util.function.BiPredicate<String, String> {
        @Override
        public boolean test(String name, String value) {
            return true;
        }
    }

    private static final class NoOpSubscription implements Flow.Subscription {
        static final NoOpSubscription INSTANCE = new NoOpSubscription();

        @Override
        public void request(long n) {
            // no-op
        }

        @Override
        public void cancel() {
            // no-op
        }
    }

    private static final class StreamSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private final Supplier<? extends InputStream> supplier;
        private volatile boolean cancelled;
        private volatile boolean started;

        StreamSubscription(Flow.Subscriber<? super ByteBuffer> subscriber,
                           Supplier<? extends InputStream> supplier) {
            this.subscriber = subscriber;
            this.supplier = supplier;
        }

        @Override
        public void request(long n) {
            if (n <= 0 || cancelled || started) {
                return;
            }
            started = true;
            InputStream input;
            try {
                input = supplier.get();
            } catch (RuntimeException e) {
                subscriber.onError(e);
                return;
            }
            if (input == null) {
                subscriber.onComplete();
                return;
            }
            try (InputStream in = input) {
                byte[] buffer = new byte[8192];
                int read;
                while (!cancelled && (read = in.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    subscriber.onNext(ByteBuffer.wrap(buffer, 0, read));
                }
                if (!cancelled) {
                    subscriber.onComplete();
                }
            } catch (IOException e) {
                if (!cancelled) {
                    subscriber.onError(e);
                }
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
