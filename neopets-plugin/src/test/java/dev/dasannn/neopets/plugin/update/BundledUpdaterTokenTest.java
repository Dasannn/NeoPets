package dev.dasannn.neopets.plugin.update;

import static org.junit.jupiter.api.Assertions.*;

import dev.dasannn.neopets.plugin.config.UpdaterSettings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledUpdaterTokenTest {
    @TempDir Path directory;

    @Test void precedencePreservesExplicitlyEmptyEnvironmentOverride() {
        var configured = settings("config-token");
        assertEquals("env-token", configured.token("env-token", "bundled-token"));
        assertEquals("", configured.token("", "bundled-token"));
        assertEquals("config-token", configured.token(null, "bundled-token"));
        assertEquals("bundled-token", settings("").token(null, "bundled-token"));
        assertEquals("bundled-token", settings("  ").token(null, "bundled-token"));
        assertEquals("", settings("").token(null, ""));
    }

    @Test void absentEmptyOrMalformedResourceReturnsNoTokenWithoutNetwork() {
        assertEquals("", BundledUpdaterToken.load()); // Normal builds have no bundled resource.
        assertEquals("", BundledUpdaterToken.read(null));
        for (String content : List.of("", "  ", "\n", "secret\ninvalid")) {
            var updater = new SelfUpdater((uri, token, accept) -> {
                fail("No token must not access GitHub"); return null;
            }, () -> read(content));
            assertEquals(UpdateResult.Failure.NO_TOKEN, run(updater, settings(""), null));
        }
        var updater = new SelfUpdater((uri, token, accept) -> {
            fail("Absent token must not access GitHub"); return null;
        }, () -> BundledUpdaterToken.read(null));
        assertEquals(UpdateResult.Failure.NO_TOKEN, run(updater, settings(""), null));
        assertEquals("", BundledUpdaterToken.read(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("secret-token"); }
        }));
    }

    @Test void selectedTokenReachesTransportAndNeverAppearsInResults() {
        for (String source : List.of("env", "config", "bundled")) {
            String selected = source + "-token";
            var configured = settings(source.equals("config") ? selected : "");
            String environment = source.equals("env") ? selected : null;
            var updater = new SelfUpdater((uri, token, accept) -> {
                assertEquals(selected, token);
                return ("{\"tag_name\":\"v2.0.0\",\"name\":\"" + token
                        + "\",\"body\":\"" + token + "\",\"assets\":[]}").getBytes(StandardCharsets.UTF_8);
            }, () -> read("bundled-token"));
            var checked = assertInstanceOf(UpdateResult.Checked.class, run(updater, configured, environment));
            assertEquals("[REDACTED]", checked.name());
            assertEquals("[REDACTED]", checked.notes());
            assertFalse(checked.toString().contains(selected));
            var exploding = new SelfUpdater((uri, token, accept) -> {
                assertEquals(selected, token);
                throw new IOException(token);
            }, () -> read("bundled-token"));
            var failure = run(exploding, configured, environment);
            assertEquals(UpdateResult.Failure.IO_ERROR, failure);
            assertFalse(failure.toString().contains(selected));
            assertFalse(configured.toString().contains(selected));
        }
    }

    @Test void higherPriorityCredentialsDoNotReadBundledResource() {
        var updater = new SelfUpdater((uri, token, accept) -> {
            throw new IOException(token);
        }, () -> { fail("Fallback must not be read"); return ""; });
        assertEquals(UpdateResult.Failure.NO_TOKEN, run(updater, settings("config-token"), ""));
        assertEquals(UpdateResult.Failure.IO_ERROR, run(updater, settings("config-token"), null));
        assertEquals(UpdateResult.Failure.IO_ERROR, run(updater, settings(""), "env-token"));
    }

    private UpdateResult run(SelfUpdater updater, UpdaterSettings settings, String environment) {
        return updater.run(settings, environment, "1.0.0", true, () -> null, directory, "current.jar");
    }

    private static UpdaterSettings settings(String token) {
        return new UpdaterSettings(true, "Dasannn/NeoPets", token, false);
    }

    private static String read(String value) {
        return BundledUpdaterToken.read(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
}
