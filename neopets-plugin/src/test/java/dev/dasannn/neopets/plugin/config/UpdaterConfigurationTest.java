package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UpdaterConfigurationTest {
    @TempDir Path directory;

    @Test void migrationAddsDefaultsAndLanguageWithoutReplacingCustomValues() throws Exception {
        var loader = new ConfigurationLoader(directory);
        var defaults = loader.load().updater();
        assertTrue(defaults.enabled()); assertFalse(defaults.includePrereleases());
        assertEquals("Dasannn/NeoPets", defaults.repository()); assertEquals("", defaults.githubToken());
        var root = YamlConfiguration.loadConfiguration(directory.resolve("config.yml").toFile());
        root.set("config-version", 16); root.set("updater", null);
        root.set("updater.repository", "owner/custom"); root.set("updater.github-token", "private-token");
        root.save(directory.resolve("config.yml").toFile());
        var lang = YamlConfiguration.loadConfiguration(directory.resolve("lang/es.yml").toFile());
        lang.set("updater", null); lang.set("updater.busy", "Personalizado");
        lang.save(directory.resolve("lang/es.yml").toFile());
        var upgraded = loader.load();
        assertEquals(ConfigurationMigrator.CURRENT_VERSION, upgraded.settings().configVersion());
        assertEquals("owner/custom", upgraded.updater().repository());
        assertEquals("private-token", upgraded.updater().githubToken());
        assertTrue(upgraded.updater().enabled());
        assertEquals("Personalizado", upgraded.language().find("updater.busy").orElseThrow());
        assertTrue(upgraded.language().find("updater.bad-signature").isPresent());
    }

    @Test void validationRejectsInvalidTypesAndPathsWithoutPrintingCredentials() throws Exception {
        var loader = new ConfigurationLoader(directory); loader.load();
        Path file = directory.resolve("config.yml");
        String original = Files.readString(file);
        var invalid = java.util.Map.<String, Object>of("updater.enabled", "secret-token",
                "updater.include-prereleases", 1, "updater.repository", "https://github.com/private/secret-token",
                "updater.github-token", java.util.List.of("secret-token"));
        for (var entry : invalid.entrySet()) {
            var root = new YamlConfiguration(); root.loadFromString(original);
            root.set(entry.getKey(), entry.getValue()); root.save(file.toFile());
            var failure = assertThrows(ConfigurationLoadException.class, loader::load);
            assertFalse(failure.getMessage().contains("secret-token"));
            assertTrue(failure.issues().stream().anyMatch(issue -> issue.path().equals(entry.getKey())));
        }
        Files.writeString(file, original + "\nupdater: {github-token: [secret-token\n");
        var failure = assertThrows(ConfigurationLoadException.class, loader::load);
        assertFalse(failure.getMessage().contains("secret-token"));
    }
}
