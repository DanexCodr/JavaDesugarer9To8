package test;

import j9compat.HttpClient;
import j9compat.HttpRequest;
import j9compat.HttpResponse;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
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
}
