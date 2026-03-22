package j9compat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/**
 * Minimal Java 8-compatible backport of {@code java.net.http.HttpClient}.
 */
public abstract class HttpClient {

    public enum Redirect {
        NEVER,
        ALWAYS,
        NORMAL
    }

    public enum Version {
        HTTP_1_1,
        HTTP_2
    }

    public interface Builder {
        Builder cookieHandler(java.net.CookieHandler cookieHandler);
        Builder connectTimeout(Duration duration);
        Builder followRedirects(Redirect redirect);
        Builder proxy(ProxySelector proxySelector);
        Builder authenticator(Authenticator authenticator);
        Builder version(Version version);
        Builder executor(Executor executor);
        Builder priority(int priority);
        Builder sslContext(SSLContext sslContext);
        Builder sslParameters(SSLParameters sslParameters);
        HttpClient build();
    }

    public static HttpClient newHttpClient() {
        return newBuilder().build();
    }

    public static Builder newBuilder() {
        return new BuilderImpl();
    }

    public abstract Optional<java.net.CookieHandler> cookieHandler();

    public abstract Optional<Duration> connectTimeout();

    public abstract Redirect followRedirects();

    public abstract Optional<ProxySelector> proxy();

    public abstract SSLContext sslContext();

    public abstract SSLParameters sslParameters();

    public abstract Optional<Authenticator> authenticator();

    public abstract Version version();

    public abstract Optional<Executor> executor();

    public abstract <T> HttpResponse<T> send(HttpRequest request,
                                             HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException, InterruptedException;

    public abstract <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                     HttpResponse.BodyHandler<T> responseBodyHandler);

    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                            HttpResponse.BodyHandler<T> responseBodyHandler,
                                                            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return sendAsync(request, responseBodyHandler);
    }

    private static final class BuilderImpl implements Builder {
        private java.net.CookieHandler cookieHandler;
        private Duration connectTimeout;
        private Redirect followRedirects = Redirect.NEVER;
        private ProxySelector proxySelector;
        private Authenticator authenticator;
        private Version version = Version.HTTP_1_1;
        private Executor executor;
        private int priority;
        private SSLContext sslContext;
        private SSLParameters sslParameters;

        @Override
        public Builder cookieHandler(java.net.CookieHandler cookieHandler) {
            if (cookieHandler == null) {
                throw new NullPointerException("cookieHandler");
            }
            this.cookieHandler = cookieHandler;
            return this;
        }

        @Override
        public Builder connectTimeout(Duration duration) {
            if (duration == null) {
                throw new NullPointerException("duration");
            }
            this.connectTimeout = duration;
            return this;
        }

        @Override
        public Builder followRedirects(Redirect redirect) {
            if (redirect == null) {
                throw new NullPointerException("redirect");
            }
            this.followRedirects = redirect;
            return this;
        }

        @Override
        public Builder proxy(ProxySelector proxySelector) {
            if (proxySelector == null) {
                throw new NullPointerException("proxySelector");
            }
            this.proxySelector = proxySelector;
            return this;
        }

        @Override
        public Builder authenticator(Authenticator authenticator) {
            if (authenticator == null) {
                throw new NullPointerException("authenticator");
            }
            this.authenticator = authenticator;
            return this;
        }

        @Override
        public Builder version(Version version) {
            if (version == null) {
                throw new NullPointerException("version");
            }
            this.version = version;
            return this;
        }

        @Override
        public Builder executor(Executor executor) {
            if (executor == null) {
                throw new NullPointerException("executor");
            }
            this.executor = executor;
            return this;
        }

        @Override
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        @Override
        public Builder sslContext(SSLContext sslContext) {
            if (sslContext == null) {
                throw new NullPointerException("sslContext");
            }
            this.sslContext = sslContext;
            return this;
        }

        @Override
        public Builder sslParameters(SSLParameters sslParameters) {
            if (sslParameters == null) {
                throw new NullPointerException("sslParameters");
            }
            this.sslParameters = sslParameters;
            return this;
        }

        @Override
        public HttpClient build() {
            return new HttpClientImpl(cookieHandler, connectTimeout, followRedirects,
                    proxySelector, authenticator, version, executor, priority,
                    sslContext, sslParameters);
        }
    }

    private static final class HttpClientImpl extends HttpClient {
        private final java.net.CookieHandler cookieHandler;
        private final Duration connectTimeout;
        private final Redirect followRedirects;
        private final ProxySelector proxySelector;
        private final Authenticator authenticator;
        private final Version version;
        private final Executor executor;
        private final int priority;
        private final SSLContext sslContext;
        private final SSLParameters sslParameters;

        private HttpClientImpl(java.net.CookieHandler cookieHandler,
                               Duration connectTimeout,
                               Redirect followRedirects,
                               ProxySelector proxySelector,
                               Authenticator authenticator,
                               Version version,
                               Executor executor,
                               int priority,
                               SSLContext sslContext,
                               SSLParameters sslParameters) {
            this.cookieHandler = cookieHandler;
            this.connectTimeout = connectTimeout;
            this.followRedirects = followRedirects == null ? Redirect.NEVER : followRedirects;
            this.proxySelector = proxySelector;
            this.authenticator = authenticator;
            this.version = version == null ? Version.HTTP_1_1 : version;
            this.executor = executor;
            this.priority = priority;
            this.sslContext = sslContext;
            this.sslParameters = sslParameters;
        }

        @Override
        public Optional<java.net.CookieHandler> cookieHandler() {
            return Optional.ofNullable(cookieHandler);
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.ofNullable(connectTimeout);
        }

        @Override
        public Redirect followRedirects() {
            return followRedirects;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.ofNullable(proxySelector);
        }

        @Override
        public SSLContext sslContext() {
            if (sslContext != null) {
                return sslContext;
            }
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return sslParameters == null ? new SSLParameters() : sslParameters;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.ofNullable(authenticator);
        }

        @Override
        public Version version() {
            return version;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.ofNullable(executor);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request,
                                        HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            if (request == null) {
                throw new NullPointerException("request");
            }
            if (responseBodyHandler == null) {
                throw new NullPointerException("responseBodyHandler");
            }
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            HttpURLConnection connection = openConnection(request.uri());
            try {
                configureConnection(connection, request);
                byte[] payload = requestPayload(request.bodyPublisher());
                if (payload != null && (payload.length > 0 || shouldSendEmptyBody(request.method()))) {
                    connection.setDoOutput(true);
                    connection.setFixedLengthStreamingMode(payload.length);
                    java.io.OutputStream out = connection.getOutputStream();
                    out.write(payload);
                    out.flush();
                    out.close();
                }

                int statusCode = connection.getResponseCode();
                HttpHeaders responseHeaders = HttpHeaders.of(connection.getHeaderFields(),
                        new AcceptAllFilter());
                ResponseInfoImpl info = new ResponseInfoImpl(statusCode, responseHeaders, version);
                HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(info);
                if (subscriber == null) {
                    throw new NullPointerException("responseBodyHandler returned null");
                }
                subscriber.onSubscribe(NoOpSubscription.INSTANCE);

                InputStream input = null;
                try {
                    input = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
                    if (input == null) {
                        input = new ByteArrayInputStream(new byte[0]);
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                        subscriber.onNext(Collections.singletonList(bytes));
                    }
                    subscriber.onComplete();
                } finally {
                    if (input != null) {
                        input.close();
                    }
                }

                T body = awaitBody(subscriber.getBody());
                return new HttpResponseImpl<T>(statusCode, responseHeaders, body,
                        request, request.uri(), version);
            } finally {
                connection.disconnect();
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                                final HttpResponse.BodyHandler<T> responseBodyHandler) {
            Executor exec = executor == null ? ForkJoinPool.commonPool() : executor;
            return CompletableFuture.supplyAsync(new java.util.function.Supplier<HttpResponse<T>>() {
                @Override
                public HttpResponse<T> get() {
                    try {
                        return send(request, responseBodyHandler);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new CompletionException(e);
                    }
                }
            }, exec);
        }

        private HttpURLConnection openConnection(URI uri) throws IOException {
            if (uri == null) {
                throw new NullPointerException("uri");
            }
            URL url = uri.toURL();
            if (proxySelector != null) {
                List<Proxy> proxies = proxySelector.select(uri);
                if (proxies != null && !proxies.isEmpty()) {
                    Proxy proxy = proxies.get(0);
                    return (HttpURLConnection) url.openConnection(proxy);
                }
            }
            return (HttpURLConnection) url.openConnection();
        }

        private void configureConnection(HttpURLConnection connection, HttpRequest request) throws IOException {
            connection.setRequestMethod(request.method());
            if (connectTimeout != null) {
                connection.setConnectTimeout(toTimeoutMillis(connectTimeout));
            }
            Optional<Duration> readTimeout = request.timeout();
            if (readTimeout.isPresent()) {
                connection.setReadTimeout(toTimeoutMillis(readTimeout.get()));
            }
            connection.setInstanceFollowRedirects(followRedirects != Redirect.NEVER);
            for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
        }

        private int toTimeoutMillis(Duration duration) {
            long millis = duration.toMillis();
            if (millis > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (millis < 0) {
                return 0;
            }
            return (int) millis;
        }

        private byte[] requestPayload(Optional<HttpRequest.BodyPublisher> publisher) {
            if (!publisher.isPresent()) {
                return null;
            }
            HttpRequest.BodyPublisher bodyPublisher = publisher.get();
            if (bodyPublisher instanceof HttpRequest.ByteArrayBodyPublisher) {
                return ((HttpRequest.ByteArrayBodyPublisher) bodyPublisher).bytes();
            }
            throw new UnsupportedOperationException("Unsupported BodyPublisher implementation: "
                    + bodyPublisher.getClass().getName());
        }

        private boolean shouldSendEmptyBody(String method) {
            if (method == null) {
                return false;
            }
            String normalized = method.toUpperCase();
            return "POST".equals(normalized) || "PUT".equals(normalized);
        }

        private <T> T awaitBody(CompletionStage<T> stage) throws IOException, InterruptedException {
            try {
                return stage.toCompletableFuture().get();
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new IOException(cause);
            }
        }
    }

    private static final class ResponseInfoImpl implements HttpResponse.ResponseInfo {
        private final int statusCode;
        private final HttpHeaders headers;
        private final Version version;

        private ResponseInfoImpl(int statusCode, HttpHeaders headers, Version version) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.version = version;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public Version version() {
            return version;
        }
    }

    private static final class HttpResponseImpl<T> implements HttpResponse<T> {
        private final int statusCode;
        private final HttpHeaders headers;
        private final T body;
        private final HttpRequest request;
        private final URI uri;
        private final Version version;

        private HttpResponseImpl(int statusCode, HttpHeaders headers, T body,
                                 HttpRequest request, URI uri, Version version) {
            this.statusCode = statusCode;
            this.headers = headers;
            this.body = body;
            this.request = request;
            this.uri = uri;
            this.version = version;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public Version version() {
            return version;
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

}
