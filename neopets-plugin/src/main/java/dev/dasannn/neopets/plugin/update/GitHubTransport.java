package dev.dasannn.neopets.plugin.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Bounded HTTP requests with explicit redirect credential policy. */
public final class GitHubTransport {
    private final RequestSender sender;

    /** Creates an HTTP client that never forwards headers through automatic redirects. */
    public GitHubTransport() {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        sender = request -> client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    GitHubTransport(RequestSender sender) { this.sender = sender; }

    @FunctionalInterface
    interface RequestSender {
        HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException;
    }

    /** Fetches bytes, sending credentials only to HTTPS github.com or api.github.com. */
    public byte[] get(URI initial, String token, String accept) throws IOException, InterruptedException, HttpFailure {
        URI uri = initial;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if ("https".equals(initial.getScheme()) && !"https".equals(uri.getScheme())) throw new IOException("Unsafe redirect");
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "NeoPets-Updater").header("Accept", accept);
            if ("https".equals(uri.getScheme())
                    && ("api.github.com".equalsIgnoreCase(uri.getHost()) || "github.com".equalsIgnoreCase(uri.getHost()))) {
                request.header("Authorization", "Bearer " + token);
            }
            var response = sender.send(request.GET().build());
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                uri = uri.resolve(response.headers().firstValue("Location").orElseThrow(() -> new IOException("No redirect")));
                continue;
            }
            if (status == 200) return response.body();
            UpdateResult.Failure failure = switch (status) {
                case 401 -> UpdateResult.Failure.UNAUTHORIZED;
                case 403 -> response.headers().firstValue("X-RateLimit-Remaining").orElse("").equals("0")
                        || response.headers().firstValue("Retry-After").isPresent()
                        ? UpdateResult.Failure.RATE_LIMITED : UpdateResult.Failure.FORBIDDEN;
                case 404 -> UpdateResult.Failure.NOT_FOUND;
                case 429 -> UpdateResult.Failure.RATE_LIMITED;
                default -> UpdateResult.Failure.IO_ERROR;
            };
            throw new HttpFailure(failure);
        }
        throw new IOException("Too many redirects");
    }

    /** HTTP status reduced to a safe code without remote response text. */
    public static final class HttpFailure extends Exception {
        private final UpdateResult.Failure failure;
        HttpFailure(UpdateResult.Failure failure) { super(failure.key()); this.failure = failure; }
        public UpdateResult.Failure failure() { return failure; }
    }
}
