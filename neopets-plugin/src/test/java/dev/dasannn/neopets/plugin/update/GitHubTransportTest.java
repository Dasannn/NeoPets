package dev.dasannn.neopets.plugin.update;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GitHubTransportTest {
    @Test void redirectStripsAuthorizationOnExternalHost() throws Exception {
        var sourceAuth = new AtomicReference<String>();
        var destinationAuth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/asset", exchange -> {
            sourceAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().add("Location", "https://assets.example.test/download");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/download", exchange -> {
            destinationAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, 3);
            exchange.getResponseBody().write(new byte[]{1, 2, 3}); exchange.close();
        });
        server.start();
        try (var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) {
            // Route logical HTTPS hosts to an actual local HttpServer while preserving outgoing headers.
            var transport = new GitHubTransport(request -> {
                var local = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                        + request.uri().getPath()));
                request.headers().map().forEach((key, values) -> values.forEach(value -> local.header(key, value)));
                assertEquals("NeoPets-Updater", request.headers().firstValue("User-Agent").orElseThrow());
                assertEquals("application/octet-stream", request.headers().firstValue("Accept").orElseThrow());
                assertTrue(request.timeout().isPresent());
                return client.send(local.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            });
            assertArrayEquals(new byte[]{1, 2, 3}, transport.get(URI.create("https://api.github.com/asset"),
                    "secret", "application/octet-stream"));
            assertEquals("Bearer secret", sourceAuth.get());
            assertNull(destinationAuth.get());
        } finally { server.stop(0); }
    }

    @Test void httpStatusesBecomeCredentialFreeFailures() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = Integer.parseInt(exchange.getRequestURI().getPath().substring(1));
            if (exchange.getRequestURI().getQuery() != null) exchange.getResponseHeaders().add("X-RateLimit-Remaining", "0");
            exchange.sendResponseHeaders(status, -1); exchange.close();
        });
        server.start();
        try {
            var transport = new GitHubTransport();
            var expected = java.util.Map.of("401", UpdateResult.Failure.UNAUTHORIZED,
                    "403", UpdateResult.Failure.FORBIDDEN, "404", UpdateResult.Failure.NOT_FOUND,
                    "429", UpdateResult.Failure.RATE_LIMITED, "403?rate", UpdateResult.Failure.RATE_LIMITED);
            for (var entry : expected.entrySet()) {
                var error = assertThrows(GitHubTransport.HttpFailure.class, () -> transport.get(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + entry.getKey()),
                        "secret", "application/vnd.github+json"));
                assertEquals(entry.getValue(), error.failure());
                assertFalse(error.getMessage().contains("secret"));
            }
        } finally { server.stop(0); }
    }
}
