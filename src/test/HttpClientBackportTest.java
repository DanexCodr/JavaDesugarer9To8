package test;

import j9compat.HttpClient;
import j9compat.HttpRequest;
import j9compat.HttpResponse;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static test.BackportTestRunner.*;

/**
 * Tests for {@link j9compat.HttpClient} backport.
 */
public final class HttpClientBackportTest {

    static void run() {
        section("HttpClientBackport");
        testGetRequest();
        testPostRequest();
        testSendAsync();
        testStreamingBodyPublisher();
        testInputStreamBodyHandler();
        testFileBodyHandler();
    }

    private static void testGetRequest() {
        HttpServer server = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/hello");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "HttpClient GET: status code");
            assertEquals("hello", response.body(), "HttpClient GET: response body");
        } catch (Exception e) {
            fail("HttpClient GET: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void testPostRequest() {
        HttpServer server = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/echo");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofString("ping"))
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            assertEquals(200, response.statusCode(), "HttpClient POST: status code");
            assertEquals("ping", new String(response.body(), "UTF-8"), "HttpClient POST: response body");
        } catch (Exception e) {
            fail("HttpClient POST: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void testSendAsync() {
        HttpServer server = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/hello");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();
            CompletableFuture<HttpResponse<String>> future = client.sendAsync(request,
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response = future.get(5, TimeUnit.SECONDS);

            assertEquals(200, response.statusCode(), "HttpClient sendAsync: status code");
            assertEquals("hello", response.body(), "HttpClient sendAsync: response body");
        } catch (Exception e) {
            fail("HttpClient sendAsync: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void testStreamingBodyPublisher() {
        HttpServer server = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/echo");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.ofInputStream(() ->
                            new ByteArrayInputStream("streaming".getBytes(StandardCharsets.UTF_8))))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode(), "HttpClient streaming body: status code");
            assertEquals("streaming", response.body(), "HttpClient streaming body: response body");

            HttpRequest flowRequest = HttpRequest.newBuilder(uri)
                    .POST(HttpRequest.BodyPublishers.fromPublisher(new FlowPublisher("flow")))
                    .build();
            HttpResponse<String> flowResponse = client.send(flowRequest, HttpResponse.BodyHandlers.ofString());
            assertEquals("flow", flowResponse.body(), "HttpClient flow publisher: response body");
        } catch (Exception e) {
            fail("HttpClient streaming body: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void testInputStreamBodyHandler() {
        HttpServer server = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/hello");

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String body = readAll(response.body());

            assertEquals(200, response.statusCode(), "HttpClient ofInputStream: status code");
            assertEquals("hello", body, "HttpClient ofInputStream: response body");
        } catch (Exception e) {
            fail("HttpClient ofInputStream: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    private static void testFileBodyHandler() {
        HttpServer server = null;
        Path tempFile = null;
        try {
            server = startServer();
            int port = server.getAddress().getPort();
            URI uri = new URI("http://127.0.0.1:" + port + "/hello");

            tempFile = Files.createTempFile("httpclient", ".txt");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .GET()
                    .build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(tempFile));
            String body = new String(Files.readAllBytes(response.body()), "UTF-8");

            assertEquals(200, response.statusCode(), "HttpClient ofFile: status code");
            assertEquals("hello", body, "HttpClient ofFile: response body");
        } catch (Exception e) {
            fail("HttpClient ofFile: unexpected exception " + e.getClass().getSimpleName());
        } finally {
            if (server != null) {
                server.stop(0);
            }
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static HttpServer startServer() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "hello".getBytes("UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            OutputStream out = exchange.getResponseBody();
            out.write(body);
            out.close();
        });
        server.createContext("/echo", exchange -> {
            InputStream in = exchange.getRequestBody();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                }
            }
            byte[] body = buffer.toByteArray();
            exchange.sendResponseHeaders(200, body.length);
            OutputStream out = exchange.getResponseBody();
            out.write(body);
            out.close();
        });
        server.start();
        return server;
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;
        while ((read = input.read(chunk)) >= 0) {
            if (read > 0) {
                buffer.write(chunk, 0, read);
            }
        }
        return new String(buffer.toByteArray(), "UTF-8");
    }

    private static final class FlowPublisher implements j9compat.Flow.Publisher<ByteBuffer> {
        private final byte[] payload;

        private FlowPublisher(String payload) {
            this.payload = payload.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void subscribe(j9compat.Flow.Subscriber<? super ByteBuffer> subscriber) {
            if (subscriber == null) {
                return;
            }
            subscriber.onSubscribe(new j9compat.Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long n) {
                    if (done) {
                        return;
                    }
                    done = true;
                    subscriber.onNext(ByteBuffer.wrap(payload));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }
    }
}
