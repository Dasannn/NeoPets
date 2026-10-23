package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationMigratorTest {
    @Test
    void appliesRegisteredAdjacentMigrationsAndUpdatesVersion() {
        var migration = new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 1;
            }

            @Override
            public int toVersion() {
                return 2;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                configuration.set("migrated", true);
            }
        };
        var configuration = new YamlConfiguration();
        configuration.set("config-version", 1);

        assertTrue(new ConfigurationMigrator(2, List.of(migration)).migrate(configuration, 1));
        assertEquals(2, configuration.getInt("config-version"));
        assertTrue(configuration.getBoolean("migrated"));
    }

    @Test
    void rejectsFutureAndUnmigratableVersionsClearly() {
        var migrator = new ConfigurationMigrator();
        var configuration = new YamlConfiguration();

        assertThrows(IllegalArgumentException.class, () -> migrator.migrate(configuration, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigurationMigrator(2, List.of()).migrate(configuration, 1));
    }
}
