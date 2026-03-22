package j9compat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.SSLSocket;

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
        private final JdkHttpClientAdapter jdkAdapter;

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
            this.jdkAdapter = JdkHttpClientAdapter.create(this);
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
            if (jdkAdapter != null) {
                return jdkAdapter.send(request, responseBodyHandler);
            }
            HttpURLConnection connection = openConnection(request.uri());
            try {
                configureConnection(connection, request);
                writeRequestBody(connection, request);

                int statusCode = connection.getResponseCode();
                HttpHeaders responseHeaders = HttpHeaders.of(connection.getHeaderFields(),
                        new AcceptAllFilter());
                Version responseVersion = request.version().isPresent()
                        ? request.version().get()
                        : version;
                ResponseInfoImpl info = new ResponseInfoImpl(statusCode, responseHeaders, responseVersion);
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
                    streamResponseBody(input, subscriber);
                } finally {
                    if (input != null) {
                        input.close();
                    }
                }

                T body = awaitBody(subscriber.getBody());
                return new HttpResponseImpl<T>(statusCode, responseHeaders, body,
                        request, request.uri(), responseVersion);
            } finally {
                connection.disconnect();
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                                final HttpResponse.BodyHandler<T> responseBodyHandler) {
            if (jdkAdapter != null) {
                return jdkAdapter.sendAsync(request, responseBodyHandler);
            }
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

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(final HttpRequest request,
                                                                final HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                final HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            if (jdkAdapter != null) {
                return jdkAdapter.sendAsync(request, responseBodyHandler, pushPromiseHandler);
            }
            CompletableFuture<HttpResponse<T>> future = sendAsync(request, responseBodyHandler);
            if (pushPromiseHandler == null) {
                return future;
            }
            return future.thenApply(response -> {
                handleLinkPushPromises(request, response.headers(), responseBodyHandler, pushPromiseHandler);
                return response;
            });
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
            if (request.expectContinue()) {
                connection.setRequestProperty("Expect", "100-continue");
            }
            for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
                for (String value : entry.getValue()) {
                    connection.addRequestProperty(entry.getKey(), value);
                }
            }
            applySslSettings(connection);
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

        private void writeRequestBody(HttpURLConnection connection, HttpRequest request)
                throws IOException, InterruptedException {
            Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
            if (!publisher.isPresent()) {
                return;
            }
            HttpRequest.BodyPublisher bodyPublisher = publisher.get();
            long length = bodyPublisher.contentLength();
            boolean emptyBody = length == 0 && shouldSendEmptyBody(request.method());
            if (length <= 0 && !emptyBody && bodyPublisher instanceof HttpRequest.ByteArrayBodyPublisher) {
                return;
            }
            connection.setDoOutput(true);
            if (length >= 0) {
                connection.setFixedLengthStreamingMode(length);
            } else {
                connection.setChunkedStreamingMode(0);
            }
            OutputStream out = connection.getOutputStream();
            BodyPublisherWriter writer = new BodyPublisherWriter(out);
            bodyPublisher.subscribe(writer);
            writer.await();
        }

        private boolean shouldSendEmptyBody(String method) {
            if (method == null) {
                return false;
            }
            String normalized = method.toUpperCase();
            return "POST".equals(normalized) || "PUT".equals(normalized);
        }

        private void applySslSettings(HttpURLConnection connection) {
            if (!(connection instanceof HttpsURLConnection)) {
                return;
            }
            HttpsURLConnection https = (HttpsURLConnection) connection;
            SSLContext context = sslContext();
            if (context != null) {
                SSLSocketFactory factory = context.getSocketFactory();
                if (sslParameters != null) {
                    factory = new ConfiguredSSLSocketFactory(factory, sslParameters);
                }
                https.setSSLSocketFactory(factory);
            }
        }

        private <T> void streamResponseBody(InputStream input, HttpResponse.BodySubscriber<T> subscriber)
                throws IOException {
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

        private <T> void handleLinkPushPromises(HttpRequest initiatingRequest,
                                                HttpHeaders headers,
                                                HttpResponse.BodyHandler<T> responseBodyHandler,
                                                HttpResponse.PushPromiseHandler<T> handler) {
            if (headers == null || handler == null) {
                return;
            }
            List<String> links = headers.allValues("Link");
            if (links.isEmpty()) {
                return;
            }
            for (String header : links) {
                if (header == null) {
                    continue;
                }
                String[] entries = header.split(",");
                for (String entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    String trimmed = entry.trim();
                    int start = trimmed.indexOf('<');
                    int end = trimmed.indexOf('>');
                    if (start < 0 || end <= start) {
                        continue;
                    }
                    String uriRef = trimmed.substring(start + 1, end);
                    if (!isPushRelation(trimmed)) {
                        continue;
                    }
                    URI targetUri = initiatingRequest.uri().resolve(uriRef);
                    HttpRequest pushRequest = HttpRequest.newBuilder(targetUri)
                            .GET()
                            .build();
                    handler.applyPushPromise(initiatingRequest, pushRequest,
                            new Function<HttpResponse.BodyHandler<T>, CompletableFuture<HttpResponse<T>>>() {
                                @Override
                                public CompletableFuture<HttpResponse<T>> apply(
                                        HttpResponse.BodyHandler<T> bodyHandler) {
                                    return sendAsync(pushRequest, bodyHandler);
                                }
                            });
                }
            }
        }

        private boolean isPushRelation(String linkValue) {
            String lower = linkValue.toLowerCase();
            return lower.contains("rel=preload") || lower.contains("rel=\"preload\"")
                    || lower.contains("rel=push") || lower.contains("rel=\"push\"");
        }
    }

    private static final class BodyPublisherWriter implements Flow.Subscriber<ByteBuffer> {
        private static final long BODY_WRITE_TIMEOUT_MINUTES = 5;
        private final OutputStream out;
        private final CountDownLatch done = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<Throwable>();

        private BodyPublisherWriter(OutputStream out) {
            this.out = out;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (subscription != null) {
                subscription.request(Long.MAX_VALUE);
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            if (item == null) {
                return;
            }
            ByteBuffer copy = item.slice();
            byte[] data = new byte[copy.remaining()];
            copy.get(data);
            try {
                out.write(data);
            } catch (IOException e) {
                error.compareAndSet(null, e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            error.compareAndSet(null, throwable);
            done.countDown();
        }

        @Override
        public void onComplete() {
            done.countDown();
        }

        private void await() throws IOException, InterruptedException {
            done.await(BODY_WRITE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            try {
                out.flush();
                out.close();
            } catch (IOException e) {
                error.compareAndSet(null, e);
            }
            Throwable failure = error.get();
            if (failure != null) {
                if (failure instanceof IOException) {
                    throw (IOException) failure;
                }
                if (failure instanceof InterruptedException) {
                    throw (InterruptedException) failure;
                }
                throw new IOException(failure);
            }
        }
    }

    private static final class ConfiguredSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final SSLParameters parameters;

        private ConfiguredSSLSocketFactory(SSLSocketFactory delegate, SSLParameters parameters) {
            this.delegate = delegate;
            this.parameters = parameters;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose)
                throws IOException {
            java.net.Socket socket = delegate.createSocket(s, host, port, autoClose);
            applyParameters(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port) throws IOException {
            java.net.Socket socket = delegate.createSocket(host, port);
            applyParameters(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort)
                throws IOException {
            java.net.Socket socket = delegate.createSocket(host, port, localHost, localPort);
            applyParameters(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress host, int port) throws IOException {
            java.net.Socket socket = delegate.createSocket(host, port);
            applyParameters(socket);
            return socket;
        }

        @Override
        public java.net.Socket createSocket(java.net.InetAddress address, int port,
                                            java.net.InetAddress localAddress, int localPort)
                throws IOException {
            java.net.Socket socket = delegate.createSocket(address, port, localAddress, localPort);
            applyParameters(socket);
            return socket;
        }

        private void applyParameters(java.net.Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setSSLParameters(parameters);
            }
        }
    }

    private static final class JdkHttpClientAdapter {
        private static final String CLIENT_CLASS_NAME = "java.net.http.HttpClient";
        private static final Class<?> CLIENT_CLASS = loadClass(CLIENT_CLASS_NAME);
        private static final Class<?> CLIENT_BUILDER_CLASS = loadClass(CLIENT_CLASS_NAME + "$Builder");
        private static final Class<?> REQUEST_CLASS = loadClass("java.net.http.HttpRequest");
        private static final Class<?> REQUEST_BUILDER_CLASS = loadClass("java.net.http.HttpRequest$Builder");
        private static final Class<?> RESPONSE_CLASS = loadClass("java.net.http.HttpResponse");
        private static final Class<?> RESPONSE_INFO_CLASS = loadClass("java.net.http.HttpResponse$ResponseInfo");
        private static final Class<?> BODY_HANDLERS_CLASS = loadClass("java.net.http.HttpResponse$BodyHandlers");
        private static final Class<?> BODY_PUBLISHERS_CLASS = loadClass("java.net.http.HttpRequest$BodyPublishers");
        private static final Class<?> BODY_HANDLER_CLASS = loadClass("java.net.http.HttpResponse$BodyHandler");
        private static final Class<?> BODY_PUBLISHER_CLASS = loadClass("java.net.http.HttpRequest$BodyPublisher");
        private static final Class<?> VERSION_CLASS = loadClass("java.net.http.HttpClient$Version");
        private static final Class<?> REDIRECT_CLASS = loadClass("java.net.http.HttpClient$Redirect");
        private static final Class<?> FLOW_PUBLISHER_CLASS = loadClass("java.util.concurrent.Flow$Publisher");
        private static final Class<?> FLOW_SUBSCRIBER_CLASS = loadClass("java.util.concurrent.Flow$Subscriber");
        private static final Class<?> FLOW_SUBSCRIPTION_CLASS = loadClass("java.util.concurrent.Flow$Subscription");
        private static final Class<?> PUSH_PROMISE_HANDLER_CLASS = loadClass("java.net.http.HttpResponse$PushPromiseHandler");
        private static final boolean AVAILABLE = CLIENT_CLASS != null
                && REQUEST_CLASS != null
                && BODY_HANDLERS_CLASS != null
                && BODY_PUBLISHERS_CLASS != null
                && BODY_HANDLER_CLASS != null
                && BODY_PUBLISHER_CLASS != null;

        private final Object client;
        private final Object inputStreamHandler;

        private JdkHttpClientAdapter(Object client, Object inputStreamHandler) {
            this.client = client;
            this.inputStreamHandler = inputStreamHandler;
        }

        static JdkHttpClientAdapter create(HttpClientImpl config) {
            if (!AVAILABLE) {
                return null;
            }
            try {
                Object builder = CLIENT_CLASS.getMethod("newBuilder").invoke(null);
                if (config.cookieHandler != null) {
                    invoke(builder, "cookieHandler", new Class<?>[]{java.net.CookieHandler.class},
                            config.cookieHandler);
                }
                if (config.connectTimeout != null) {
                    invoke(builder, "connectTimeout", new Class<?>[]{Duration.class}, config.connectTimeout);
                }
                if (config.followRedirects != null && REDIRECT_CLASS != null) {
                    Object redirect = enumValue(REDIRECT_CLASS, config.followRedirects.name());
                    invoke(builder, "followRedirects", new Class<?>[]{REDIRECT_CLASS}, redirect);
                }
                if (config.proxySelector != null) {
                    invoke(builder, "proxy", new Class<?>[]{ProxySelector.class}, config.proxySelector);
                }
                if (config.authenticator != null) {
                    invoke(builder, "authenticator", new Class<?>[]{Authenticator.class}, config.authenticator);
                }
                if (config.version != null && VERSION_CLASS != null) {
                    Object version = enumValue(VERSION_CLASS, config.version.name());
                    invoke(builder, "version", new Class<?>[]{VERSION_CLASS}, version);
                }
                if (config.executor != null) {
                    invoke(builder, "executor", new Class<?>[]{Executor.class}, config.executor);
                }
                if (config.priority > 0) {
                    invokeOptional(builder, "priority", new Class<?>[]{int.class}, config.priority);
                }
                if (config.sslContext != null) {
                    invoke(builder, "sslContext", new Class<?>[]{SSLContext.class}, config.sslContext);
                }
                if (config.sslParameters != null) {
                    invoke(builder, "sslParameters", new Class<?>[]{SSLParameters.class}, config.sslParameters);
                }
                Object client = invoke(builder, "build", new Class<?>[0]);
                Object inputStreamHandler = invokeStatic(BODY_HANDLERS_CLASS, "ofInputStream", new Class<?>[0]);
                return new JdkHttpClientAdapter(client, inputStreamHandler);
            } catch (Exception e) {
                return null;
            }
        }

        <T> HttpResponse<T> send(HttpRequest request,
                                 HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            try {
                Object jdkRequest = toJdkRequest(request);
                Object jdkResponse = invoke(client, "send",
                        new Class<?>[]{REQUEST_CLASS, BODY_HANDLER_CLASS},
                        jdkRequest, inputStreamHandler);
                return adaptResponse(jdkResponse, request, responseBodyHandler);
            } catch (RuntimeException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                         HttpResponse.BodyHandler<T> responseBodyHandler) {
            try {
                Object jdkRequest = toJdkRequest(request);
                Object future = invoke(client, "sendAsync",
                        new Class<?>[]{REQUEST_CLASS, BODY_HANDLER_CLASS},
                        jdkRequest, inputStreamHandler);
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> jdkFuture = (CompletableFuture<Object>) future;
                return jdkFuture.thenApply(new java.util.function.Function<Object, HttpResponse<T>>() {
                    @Override
                    public HttpResponse<T> apply(Object response) {
                        try {
                            return adaptResponse(response, request, responseBodyHandler);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }
                });
            } catch (Exception e) {
                CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<HttpResponse<T>>();
                failed.completeExceptionally(e);
                return failed;
            }
        }

        <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                         HttpResponse.BodyHandler<T> responseBodyHandler,
                                                         HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            if (pushPromiseHandler == null || PUSH_PROMISE_HANDLER_CLASS == null) {
                return sendAsync(request, responseBodyHandler);
            }
            try {
                Object jdkRequest = toJdkRequest(request);
                Object jdkPushHandler = java.lang.reflect.Proxy.newProxyInstance(
                        PUSH_PROMISE_HANDLER_CLASS.getClassLoader(),
                        new Class<?>[]{PUSH_PROMISE_HANDLER_CLASS},
                        new PushPromiseInvocationHandler<T>(this, request, responseBodyHandler, pushPromiseHandler));
                Object future = invoke(client, "sendAsync",
                        new Class<?>[]{REQUEST_CLASS, BODY_HANDLER_CLASS, PUSH_PROMISE_HANDLER_CLASS},
                        jdkRequest, inputStreamHandler, jdkPushHandler);
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> jdkFuture = (CompletableFuture<Object>) future;
                return jdkFuture.thenApply(new java.util.function.Function<Object, HttpResponse<T>>() {
                    @Override
                    public HttpResponse<T> apply(Object response) {
                        try {
                            return adaptResponse(response, request, responseBodyHandler);
                        } catch (IOException e) {
                            throw new CompletionException(e);
                        }
                    }
                });
            } catch (Exception e) {
                CompletableFuture<HttpResponse<T>> failed = new CompletableFuture<HttpResponse<T>>();
                failed.completeExceptionally(e);
                return failed;
            }
        }

        private Object toJdkRequest(HttpRequest request) throws Exception {
            Object builder = invokeStatic(REQUEST_CLASS, "newBuilder",
                    new Class<?>[]{URI.class}, request.uri());
            for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
                for (String value : entry.getValue()) {
                    invoke(builder, "header", new Class<?>[]{String.class, String.class},
                            entry.getKey(), value);
                }
            }
            Optional<Duration> timeout = request.timeout();
            if (timeout.isPresent()) {
                invoke(builder, "timeout", new Class<?>[]{Duration.class}, timeout.get());
            }
            invoke(builder, "expectContinue", new Class<?>[]{boolean.class}, request.expectContinue());
            if (request.version().isPresent() && VERSION_CLASS != null) {
                Object version = enumValue(VERSION_CLASS, request.version().get().name());
                invoke(builder, "version", new Class<?>[]{VERSION_CLASS}, version);
            }
            HttpRequest.BodyPublisher bodyPublisher = request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody());
            Object jdkPublisher = toJdkBodyPublisher(bodyPublisher);
            invoke(builder, "method", new Class<?>[]{String.class, BODY_PUBLISHER_CLASS},
                    request.method(), jdkPublisher);
            return invoke(builder, "build", new Class<?>[0]);
        }

        private Object toJdkBodyPublisher(HttpRequest.BodyPublisher publisher) throws Exception {
            if (publisher instanceof HttpRequest.ByteArrayBodyPublisher) {
                byte[] bytes = ((HttpRequest.ByteArrayBodyPublisher) publisher).bytes();
                return invokeStatic(BODY_PUBLISHERS_CLASS, "ofByteArray",
                        new Class<?>[]{byte[].class}, bytes);
            }
            if (publisher instanceof HttpRequest.InputStreamBodyPublisher) {
                HttpRequest.InputStreamBodyPublisher streamPublisher =
                        (HttpRequest.InputStreamBodyPublisher) publisher;
                return invokeStatic(BODY_PUBLISHERS_CLASS, "ofInputStream",
                        new Class<?>[]{java.util.function.Supplier.class}, streamPublisher.supplier());
            }
            if (publisher instanceof HttpRequest.FileBodyPublisher) {
                HttpRequest.FileBodyPublisher filePublisher = (HttpRequest.FileBodyPublisher) publisher;
                return invokeStatic(BODY_PUBLISHERS_CLASS, "ofFile",
                        new Class<?>[]{java.nio.file.Path.class}, filePublisher.path());
            }
            Flow.Publisher<ByteBuffer> flowPublisher = null;
            long contentLength = -1;
            if (publisher instanceof HttpRequest.PublisherBodyPublisher) {
                HttpRequest.PublisherBodyPublisher wrapped = (HttpRequest.PublisherBodyPublisher) publisher;
                flowPublisher = wrapped.publisher();
                contentLength = wrapped.contentLength();
            } else {
                flowPublisher = publisher;
                contentLength = publisher.contentLength();
            }
            Object jdkPublisher = adaptPublisher(flowPublisher);
            if (contentLength >= 0) {
                return invokeStatic(BODY_PUBLISHERS_CLASS, "fromPublisher",
                        new Class<?>[]{FLOW_PUBLISHER_CLASS, long.class}, jdkPublisher, contentLength);
            }
            return invokeStatic(BODY_PUBLISHERS_CLASS, "fromPublisher",
                    new Class<?>[]{FLOW_PUBLISHER_CLASS}, jdkPublisher);
        }

        private Object adaptPublisher(final Flow.Publisher<ByteBuffer> publisher) {
            return java.lang.reflect.Proxy.newProxyInstance(FLOW_PUBLISHER_CLASS.getClassLoader(),
                    new Class<?>[]{FLOW_PUBLISHER_CLASS},
                    (proxy, method, args) -> {
                        if ("subscribe".equals(method.getName()) && args != null && args.length == 1) {
                            Object jdkSubscriber = args[0];
                            Flow.Subscriber<ByteBuffer> compatSubscriber =
                                    new CompatSubscriber(jdkSubscriber, FLOW_SUBSCRIBER_CLASS, FLOW_SUBSCRIPTION_CLASS);
                            publisher.subscribe(compatSubscriber);
                            return null;
                        }
                        return null;
                    });
        }

        private <T> HttpResponse<T> adaptResponse(Object jdkResponse,
                                                  HttpRequest request,
                                                  HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            try {
                int statusCode = (Integer) invoke(jdkResponse, "statusCode", new Class<?>[0]);
                Object jdkHeaders = invoke(jdkResponse, "headers", new Class<?>[0]);
                @SuppressWarnings("unchecked")
                Map<String, List<String>> headerMap = (Map<String, List<String>>) invoke(
                        jdkHeaders, "map", new Class<?>[0]);
                HttpHeaders headers = HttpHeaders.of(headerMap, new AcceptAllFilter());
                Object jdkVersion = invoke(jdkResponse, "version", new Class<?>[0]);
                Version responseVersion = versionFromJdk(jdkVersion);
                ResponseInfoImpl info = new ResponseInfoImpl(statusCode, headers, responseVersion);
                HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(info);
                if (subscriber == null) {
                    throw new NullPointerException("responseBodyHandler returned null");
                }
                subscriber.onSubscribe(NoOpSubscription.INSTANCE);
                InputStream bodyStream = (InputStream) invoke(jdkResponse, "body", new Class<?>[0]);
                if (bodyStream == null) {
                    bodyStream = new ByteArrayInputStream(new byte[0]);
                }
                try (InputStream in = bodyStream) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                        subscriber.onNext(Collections.singletonList(bytes));
                    }
                    subscriber.onComplete();
                } catch (IOException e) {
                    subscriber.onError(e);
                    throw e;
                }
                T body = awaitBody(subscriber.getBody());
                URI uri = (URI) invoke(jdkResponse, "uri", new Class<?>[0]);
                return new HttpResponseImpl<T>(statusCode, headers, body, request, uri, responseVersion);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }

        private Version versionFromJdk(Object jdkVersion) {
            if (jdkVersion == null) {
                return Version.HTTP_1_1;
            }
            String name = ((Enum<?>) jdkVersion).name();
            if (Version.HTTP_2.name().equals(name)) {
                return Version.HTTP_2;
            }
            return Version.HTTP_1_1;
        }

        private <T> T awaitBody(CompletionStage<T> stage) throws IOException {
            try {
                return stage.toCompletableFuture().get();
            } catch (Exception e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                throw new IOException(e);
            }
        }

        private static Object invokeStatic(Class<?> type, String method, Class<?>[] paramTypes, Object... args)
                throws Exception {
            return invoke(type, null, method, paramTypes, args);
        }

        private static Object invoke(Object target, String method, Class<?>[] paramTypes, Object... args)
                throws Exception {
            return invoke(null, target, method, paramTypes, args);
        }

        private static Object invoke(Class<?> type,
                                     Object target,
                                     String method,
                                     Class<?>[] paramTypes,
                                     Object... args) throws Exception {
            Class<?> resolved = type != null ? type : target.getClass();
            java.lang.reflect.Method m = resolved.getMethod(method, paramTypes);
            return m.invoke(target, args);
        }

        private static Object invokeOptional(Object target, String method, Class<?>[] paramTypes, Object... args) {
            try {
                return invoke(target, method, paramTypes, args);
            } catch (Exception ignored) {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static Object enumValue(Class<?> enumClass, String name) {
            return Enum.valueOf((Class<Enum>) enumClass, name);
        }

        private static Class<?> loadClass(String name) {
            try {
                return Class.forName(name);
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static final class CompatSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final Object jdkSubscriber;
        private final Class<?> flowSubscriberClass;
        private final Class<?> flowSubscriptionClass;

        private CompatSubscriber(Object jdkSubscriber, Class<?> flowSubscriberClass, Class<?> flowSubscriptionClass) {
            this.jdkSubscriber = jdkSubscriber;
            this.flowSubscriberClass = flowSubscriberClass;
            this.flowSubscriptionClass = flowSubscriptionClass;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Object jdkSubscription = java.lang.reflect.Proxy.newProxyInstance(flowSubscriptionClass.getClassLoader(),
                    new Class<?>[]{flowSubscriptionClass},
                    (proxy, method, args) -> {
                        if ("request".equals(method.getName())) {
                            if (args != null && args.length == 1) {
                                subscription.request((Long) args[0]);
                            }
                            return null;
                        }
                        if ("cancel".equals(method.getName())) {
                            subscription.cancel();
                            return null;
                        }
                        return null;
                    });
            try {
                java.lang.reflect.Method onSubscribe = flowSubscriberClass.getMethod("onSubscribe",
                        flowSubscriptionClass);
                onSubscribe.invoke(jdkSubscriber, jdkSubscription);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onNext(ByteBuffer item) {
            try {
                java.lang.reflect.Method onNext = flowSubscriberClass.getMethod("onNext", Object.class);
                onNext.invoke(jdkSubscriber, item);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onError(Throwable throwable) {
            try {
                java.lang.reflect.Method onError = flowSubscriberClass.getMethod("onError", Throwable.class);
                onError.invoke(jdkSubscriber, throwable);
            } catch (Exception ignored) {
            }
        }

        @Override
        public void onComplete() {
            try {
                java.lang.reflect.Method onComplete = flowSubscriberClass.getMethod("onComplete");
                onComplete.invoke(jdkSubscriber);
            } catch (Exception ignored) {
            }
        }
    }

    private static final class PushPromiseInvocationHandler<T> implements java.lang.reflect.InvocationHandler {
        private final JdkHttpClientAdapter adapter;
        private final HttpRequest initiatingRequest;
        private final HttpResponse.BodyHandler<T> defaultHandler;
        private final HttpResponse.PushPromiseHandler<T> handler;

        private PushPromiseInvocationHandler(JdkHttpClientAdapter adapter,
                                             HttpRequest initiatingRequest,
                                             HttpResponse.BodyHandler<T> defaultHandler,
                                             HttpResponse.PushPromiseHandler<T> handler) {
            this.adapter = adapter;
            this.initiatingRequest = initiatingRequest;
            this.defaultHandler = defaultHandler;
            this.handler = handler;
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (!"applyPushPromise".equals(method.getName()) || args == null || args.length < 3) {
                return null;
            }
            Object jdkPushRequest = args[1];
            @SuppressWarnings("unchecked")
            Function<Object, CompletableFuture<Object>> jdkAcceptor =
                    (Function<Object, CompletableFuture<Object>>) args[2];
            HttpRequest pushRequest = toCompatRequest(jdkPushRequest);
            handler.applyPushPromise(initiatingRequest, pushRequest,
                    new Function<HttpResponse.BodyHandler<T>, CompletableFuture<HttpResponse<T>>>() {
                        @Override
                        public CompletableFuture<HttpResponse<T>> apply(
                                HttpResponse.BodyHandler<T> bodyHandler) {
                            try {
                                Object responseFuture = jdkAcceptor.apply(adapter.inputStreamHandler);
                                @SuppressWarnings("unchecked")
                                CompletableFuture<Object> jdkFuture = (CompletableFuture<Object>) responseFuture;
                                return jdkFuture.thenApply(new java.util.function.Function<Object, HttpResponse<T>>() {
                                    @Override
                                    public HttpResponse<T> apply(Object response) {
                                        try {
                                            return adapter.adaptResponse(response, pushRequest, bodyHandler);
                                        } catch (IOException e) {
                                            throw new CompletionException(e);
                                        }
                                    }
                                });
                            } catch (Exception e) {
                                CompletableFuture<HttpResponse<T>> failed =
                                        new CompletableFuture<HttpResponse<T>>();
                                failed.completeExceptionally(e);
                                return failed;
                            }
                        }
                    });
            return null;
        }

        private HttpRequest toCompatRequest(Object jdkRequest) {
            try {
                URI uri = (URI) JdkHttpClientAdapter.invoke(jdkRequest, "uri", new Class<?>[0]);
                String method = (String) JdkHttpClientAdapter.invoke(jdkRequest, "method", new Class<?>[0]);
                Object jdkHeaders = JdkHttpClientAdapter.invoke(jdkRequest, "headers", new Class<?>[0]);
                @SuppressWarnings("unchecked")
                Map<String, List<String>> headerMap = (Map<String, List<String>>) JdkHttpClientAdapter.invoke(
                        jdkHeaders, "map", new Class<?>[0]);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
                for (Map.Entry<String, List<String>> entry : headerMap.entrySet()) {
                    for (String value : entry.getValue()) {
                        builder.header(entry.getKey(), value);
                    }
                }
                return builder.method(method, HttpRequest.BodyPublishers.noBody()).build();
            } catch (Exception e) {
                return HttpRequest.newBuilder(initiatingRequest.uri()).GET().build();
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
