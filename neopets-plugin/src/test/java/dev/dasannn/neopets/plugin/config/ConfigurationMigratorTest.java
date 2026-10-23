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

        assertThrows(IllegalArgumentException.class, () -> migrator.migrate(configuration, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfigurationMigrator(2, List.of()).migrate(configuration, 1));
    }

    @Test
    void versionFiveRewritesOnlyUntouchedLegacySkillTreeValues() {
        var migrator = new ConfigurationMigrator();
        var dog = new YamlConfiguration();
        dog.set("attributes.health.values", List.of(22, 24, 26, 28, 30));
        dog.set("attributes.speed.values", List.of(0.32, 0.34, 0.36, 0.38, 0.4));
        dog.set("attributes.scent.values", List.of(3, 6, 9, 12, 15));

        assertTrue(migrator.migrateSkillTree(dog, "dog", 3));
        assertEquals(List.of(2, 4, 6, 8, 10), dog.getList("attributes.health.values"));
        assertEquals(List.of(0.05, 0.1, 0.15, 0.2, 0.25), dog.getList("attributes.speed.values"));
        assertEquals(List.of(3, 6, 9, 12, 15), dog.getList("attributes.scent.values"));
    }
}
