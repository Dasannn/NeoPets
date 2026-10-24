package dev.dasannn.neopets.plugin.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads the optional release credential without retaining it in diagnostic objects. */
final class BundledUpdaterToken {
    private BundledUpdaterToken() { }

    static String load() {
        return read(BundledUpdaterToken.class.getResourceAsStream("/neopets-updater-token.txt"));
    }

    // Owns and closes the supplied stream, including in tests.
    static String read(InputStream resource) {
        if (resource == null) return "";
        try (resource) {
            String token = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return token.isBlank() || token.chars().anyMatch(Character::isISOControl) ? "" : token;
        } catch (IOException failure) {
            return "";
        }
    }
}
