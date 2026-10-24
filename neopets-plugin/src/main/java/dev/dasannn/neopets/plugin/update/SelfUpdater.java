package dev.dasannn.neopets.plugin.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.dasannn.neopets.plugin.config.UpdaterSettings;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** Runs one check/download at a time on the runtime executor; stages only authenticated artifacts. */
public final class SelfUpdater {
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Fetcher transport;
    private final Supplier<String> bundledToken;

    /** Creates the production GitHub transport. */
    public SelfUpdater() { this(new GitHubTransport()::get); }

    SelfUpdater(Fetcher transport) { this(transport, BundledUpdaterToken::load); }

    SelfUpdater(Fetcher transport, Supplier<String> bundledToken) {
        this.transport = transport;
        this.bundledToken = bundledToken;
    }

    @FunctionalInterface
    interface Fetcher {
        byte[] get(URI uri, String token, String accept) throws Exception;
    }

    /** Claims the guard before queuing so simultaneous commands cannot both enter. */
    public CompletableFuture<UpdateResult> submit(Function<Supplier<UpdateResult>, CompletableFuture<UpdateResult>> executor,
            Supplier<UpdateResult> operation) {
        if (!busy.compareAndSet(false, true)) return CompletableFuture.completedFuture(UpdateResult.Failure.BUSY);
        try {
            return executor.apply(operation).handle((result, failure) -> {
                busy.set(false);
                return failure == null ? result : UpdateResult.Failure.IO_ERROR;
            });
        } catch (RuntimeException rejected) {
            busy.set(false);
            return CompletableFuture.completedFuture(UpdateResult.Failure.IO_ERROR);
        }
    }

    /** Blocking operation: caller must schedule on a worker, never the server thread. */
    public UpdateResult run(UpdaterSettings settings, String environmentToken, String installed, boolean check,
            Supplier<byte[]> publicKey, Path updateDirectory, String jarName) {
        if (!settings.enabled()) return UpdateResult.Failure.DISABLED;
        try {
            String token = settings.token(environmentToken,
                    environmentToken == null && settings.githubToken().isBlank() ? bundledToken.get() : "");
            if (token.isBlank()) return UpdateResult.Failure.NO_TOKEN;
            var current = SemanticVersion.parse(installed);
            JsonObject release = latest(settings, token);
            if (release == null) return UpdateResult.Failure.NOT_FOUND;
            String tag = release.get("tag_name").getAsString();
            var version = SemanticVersion.parse(tag);
            if (check) return new UpdateResult.Checked(redact(installed, token), redact(tag, token),
                    redact(text(release, "name"), token),
                    redact(text(release, "body").lines().findFirst().orElse(""), token), version.compareTo(current) > 0);
            if (version.compareTo(current) <= 0) return UpdateResult.Failure.NO_NEWER_VERSION;
            var key = UpdateVerifier.publicKey(publicKey.get());
            if (key == null) return UpdateResult.Failure.PLACEHOLDER_KEY;
            String name = "NeoPets-" + (tag.startsWith("v") ? tag.substring(1) : tag) + ".jar";
            byte[][] assets = new byte[3][];
            String[] names = {name, name + ".sha256", name + ".sig"};
            for (int i = 0; i < names.length; i++) {
                Long id = assetId(release, names[i]);
                if (id == null) return UpdateResult.Failure.MISSING_ASSET;
                assets[i] = transport.get(URI.create("https://api.github.com/repos/" + settings.repository()
                        + "/releases/assets/" + id), token, "application/octet-stream");
            }
            var failure = UpdateVerifier.verify(assets[0], assets[1], assets[2], key);
            if (failure != null) return failure;
            failure = UpdateVerifier.verifyVersion(assets[0], tag, current);
            if (failure != null) return failure;
            stage(assets[0], updateDirectory, jarName);
            return new UpdateResult.Staged(redact(tag, token));
        } catch (GitHubTransport.HttpFailure failure) {
            return failure.failure();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return UpdateResult.Failure.IO_ERROR;
        } catch (Exception failure) {
            // Never publish exception text: URLs, responses and header validation may contain credentials.
            return UpdateResult.Failure.IO_ERROR;
        }
    }

    private static String redact(String value, String token) {
        return value.replace(token, "[REDACTED]");
    }

    private JsonObject latest(UpdaterSettings settings, String token) throws Exception {
        String root = "https://api.github.com/repos/" + settings.repository() + "/releases";
        if (!settings.includePrereleases()) {
            return JsonParser.parseString(new String(transport.get(URI.create(root + "/latest"), token,
                    "application/vnd.github+json"), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        JsonObject best = null;
        SemanticVersion bestVersion = null;
        // Follow all pages: creation dates are not semantic version precedence.
        for (int page = 1; ; page++) {
            var releases = JsonParser.parseString(new String(transport.get(URI.create(root + "?per_page=100&page=" + page),
                    token, "application/vnd.github+json"), StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement element : releases) {
                var candidate = element.getAsJsonObject();
                if (candidate.get("draft").getAsBoolean()) continue;
                SemanticVersion version;
                try { version = SemanticVersion.parse(candidate.get("tag_name").getAsString()); }
                catch (IllegalArgumentException invalid) { continue; }
                if (bestVersion == null || version.compareTo(bestVersion) > 0) {
                    best = candidate;
                    bestVersion = version;
                }
            }
            if (releases.size() < 100) return best;
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static Long assetId(JsonObject release, String name) {
        for (JsonElement element : release.getAsJsonArray("assets")) {
            var asset = element.getAsJsonObject();
            if (text(asset, "name").equals(name)) return asset.get("id").getAsLong();
        }
        return null;
    }

    /** Atomically replaces the staged JAR; any failed write leaves the previous target intact. */
    static void stage(byte[] jar, Path directory, String jarName) throws Exception {
        if (!Path.of(jarName).getFileName().toString().equals(jarName)) throw new IllegalArgumentException("Invalid JAR name");
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".neopets-", ".tmp");
        try {
            Files.write(temporary, jar);
            Files.move(temporary, directory.resolve(jarName), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
