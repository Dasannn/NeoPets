package dev.dasannn.neopets.plugin.update;

import static org.junit.jupiter.api.Assertions.*;

import dev.dasannn.neopets.plugin.config.UpdaterSettings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfUpdaterTest {
    @TempDir Path directory;
    private final UpdaterSettings settings = new UpdaterSettings(true, "Dasannn/NeoPets", "secret-token", false);

    @Test void semanticPrecedence() {
        var versions = java.util.List.of("0.1.0-SNAPSHOT", "0.1.0", "0.2.0-alpha", "0.2.0-alpha.1",
                "0.2.0-alpha.2", "0.2.0-alpha.10", "0.2.0-beta", "0.2.0-rc.1", "0.2.0", "1.0.0", "10.0.0");
        for (int i = 0; i < versions.size() - 1; i++) {
            assertTrue(SemanticVersion.parse(versions.get(i)).compareTo(SemanticVersion.parse(versions.get(i+1))) < 0);
        }
        assertEquals(0, SemanticVersion.parse("v1.0.0+build.1").compareTo(SemanticVersion.parse("1.0.0+other")));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("1.0.0-01"));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse("../1"));
    }

    @Test void signaturesChecksumsAndPlaceholder() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] jar = "jar bytes".getBytes(StandardCharsets.UTF_8);
        byte[] checksum = checksum(jar);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate()); signer.update(jar);
        byte[] signature = signer.sign();
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----\n";
        var key = UpdateVerifier.publicKey(pem.getBytes(StandardCharsets.US_ASCII));
        assertNull(UpdateVerifier.verify(jar, checksum, signature, key));
        assertEquals(UpdateResult.Failure.BAD_SIGNATURE, UpdateVerifier.verify(jar, checksum, signature,
                KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic()));
        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        assertEquals(UpdateResult.Failure.CHECKSUM_MISMATCH, UpdateVerifier.verify(tampered, checksum, signature, key));
        assertEquals(UpdateResult.Failure.BAD_SIGNATURE, UpdateVerifier.verify(tampered, checksum(tampered), signature, key));
        assertNull(UpdateVerifier.publicKey(null));
        assertNull(UpdateVerifier.publicKey("PLACEHOLDER".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(UpdateResult.Failure.PLACEHOLDER_KEY, UpdateVerifier.verify(jar, checksum, signature, null));
    }

    @Test void sameAndDowngradeNeverDownloadOrWrite() {
        for (String installed : java.util.List.of("1.0.0", "2.0.0")) {
            AtomicInteger requests = new AtomicInteger();
            var updater = new SelfUpdater((uri, token, accept) -> {
                requests.incrementAndGet();
                return release("v1.0.0", "[]");
            });
            assertEquals(UpdateResult.Failure.NO_NEWER_VERSION, updater.run(settings, null, installed, false,
                    () -> { fail("must not read key"); return null; }, directory, "current.jar"));
            assertEquals(1, requests.get());
            assertFalse(Files.exists(directory.resolve("current.jar")));
        }
    }

    @Test void placeholderMissingAssetAndSafeErrors() {
        var updater = new SelfUpdater((uri, token, accept) -> release("v2.0.0", "[]"));
        assertEquals(UpdateResult.Failure.PLACEHOLDER_KEY, updater.run(settings, null, "1.0.0", false,
                () -> null, directory, "current.jar"));
        var exploding = new SelfUpdater((uri, token, accept) -> { throw new IllegalArgumentException(token); });
        var failure = exploding.run(settings, null, "1.0.0", true, () -> null, directory, "current.jar");
        assertEquals(UpdateResult.Failure.IO_ERROR, failure);
        assertFalse(failure.toString().contains("secret-token"));
        assertFalse(settings.toString().contains("secret-token"));
        assertEquals("environment", settings.token("environment"));
        assertEquals("", settings.token(""));
        assertEquals(UpdateResult.Failure.NO_TOKEN, updater.run(settings, "", "1.0.0", true,
                () -> null, directory, "current.jar"));
    }

    @Test void verifiedDownloadStagesOnlyAfterBothChecks() throws Exception {
        byte[] jar = "new plugin".getBytes(StandardCharsets.UTF_8);
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(keys.getPrivate()); signer.update(jar);
        byte[] signature = signer.sign();
        byte[] pem = ("-----BEGIN PUBLIC KEY-----\n" + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----").getBytes(StandardCharsets.US_ASCII);
        String assets = "[{\"name\":\"NeoPets-2.0.0.jar\",\"id\":1},{\"name\":\"NeoPets-2.0.0.jar.sha256\",\"id\":2},"
                + "{\"name\":\"NeoPets-2.0.0.jar.sig\",\"id\":3}]";
        var updater = new SelfUpdater((uri, token, accept) -> {
            if (uri.getPath().endsWith("latest")) return release("v2.0.0", assets);
            assertEquals("application/octet-stream", accept);
            return switch (uri.getPath().charAt(uri.getPath().length()-1)) {
                case '1' -> jar; case '2' -> checksum(jar); default -> signature;
            };
        });
        assertInstanceOf(UpdateResult.Staged.class, updater.run(settings, null, "1.0.0", false,
                () -> pem, directory, "current.jar"));
        assertArrayEquals(jar, Files.readAllBytes(directory.resolve("current.jar")));
        var missing = new SelfUpdater((uri, token, accept) -> release("v2.0.0", "[]"));
        assertEquals(UpdateResult.Failure.MISSING_ASSET, missing.run(settings, null, "1.0.0", false,
                () -> pem, directory, "current.jar"));
        assertArrayEquals(jar, Files.readAllBytes(directory.resolve("current.jar")));
    }

    @Test void guardCoversQueuedWorkAndReleasesAfterFailure() {
        var updater = new SelfUpdater();
        var pending = new CompletableFuture<UpdateResult>();
        var first = updater.submit(operation -> pending, () -> UpdateResult.Failure.IO_ERROR);
        assertEquals(UpdateResult.Failure.BUSY,
                updater.submit(operation -> { fail("must not enqueue"); return null; }, () -> null).join());
        pending.completeExceptionally(new IllegalStateException("secret-token"));
        assertEquals(UpdateResult.Failure.IO_ERROR, first.join());
        assertEquals(UpdateResult.Failure.NO_TOKEN, updater.submit(operation ->
                CompletableFuture.completedFuture(operation.get()), () -> UpdateResult.Failure.NO_TOKEN).join());
        assertEquals(UpdateResult.Failure.IO_ERROR, updater.submit(operation -> { throw new IllegalStateException(); },
                () -> null).join());
        assertEquals(UpdateResult.Failure.NO_TOKEN, updater.submit(operation ->
                CompletableFuture.completedFuture(operation.get()), () -> UpdateResult.Failure.NO_TOKEN).join());
    }

    @Test void checkComparesInstalledAndLatestAndReportsOnlyFirstNotesLine() {
        var updater = new SelfUpdater((uri, token, accept) -> {
            assertTrue(uri.getPath().endsWith("/latest"));
            assertEquals("application/vnd.github+json", accept);
            assertEquals("environment-token", token);
            return release("v1.0.0", "[]");
        });
        var checked = assertInstanceOf(UpdateResult.Checked.class, updater.run(settings, "environment-token",
                "1.0.0-SNAPSHOT", true, () -> { fail("check must not read key"); return null; }, directory, "current.jar"));
        assertTrue(checked.newer()); assertEquals("1.0.0-SNAPSHOT", checked.installed());
        assertEquals("v1.0.0", checked.latest()); assertEquals("First line", checked.notes());
        assertFalse(assertInstanceOf(UpdateResult.Checked.class, updater.run(settings, "environment-token",
                "1.0.0", true, () -> null, directory, "current.jar")).newer());
    }

    @Test void prereleasesAreSelectedByVersionAndDraftsIgnored() {
        var updater = new SelfUpdater((uri, token, accept) -> {
            assertTrue(uri.getQuery().contains("page=1"));
            String json = "["
                    + new String(release("v2.0.0-rc.1", "[]"), StandardCharsets.UTF_8).replaceFirst("\\{", "{\"draft\":false,")
                    + "," + new String(release("v1.9.0", "[]"), StandardCharsets.UTF_8).replaceFirst("\\{", "{\"draft\":false,")
                    + "," + new String(release("v9.0.0", "[]"), StandardCharsets.UTF_8).replaceFirst("\\{", "{\"draft\":true,") + "]";
            return json.getBytes(StandardCharsets.UTF_8);
        });
        var pre = new UpdaterSettings(true, "Dasannn/NeoPets", "secret", true);
        var checked = assertInstanceOf(UpdateResult.Checked.class, updater.run(pre, null, "1.0.0", true,
                () -> null, directory, "current.jar"));
        assertEquals("v2.0.0-rc.1", checked.latest());
        assertTrue(checked.newer());
    }

    private static byte[] checksum(byte[] jar) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar)).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] release(String version, String assets) {
        return ("{\"tag_name\":\"" + version + "\",\"name\":\"Release\",\"body\":\"First line\\nSecond line\","
                + "\"assets\":" + assets + "}").getBytes(StandardCharsets.UTF_8);
    }
}
