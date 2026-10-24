package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigurationMigratorTest {
    @Test
    void replacesOnlyUntouchedLegacyCommonNodesAtVersionSeventeen() throws Exception {
        var migrator = new ConfigurationMigrator();
        var replacements = java.util.Map.of("dog", "tough_hide", "cat", "padded_paws",
                "polar_bear", "polar_swimmer", "donkey", "sure_footing");
        String original = """
                attributes:
                  health:
                    effect: max_health
                  fire_resistance:
                    category: vanilla
                    max-level: 1
                    effect: fire_resistance
                    values: [1.0]
                    money-costs: [1500.0]
                    skill-point-cost: 1
                  speed:
                    effect: movement_speed
                """;
        for (var entry : replacements.entrySet()) {
            var yaml = new YamlConfiguration();
            yaml.loadFromString(original);
            assertTrue(migrator.migrateSkillTree(yaml, entry.getKey(), 16));
            assertEquals(List.of("health", entry.getValue(), "speed"),
                    List.copyOf(yaml.getConfigurationSection("attributes").getKeys(false)));
            assertEquals(1500.0, yaml.getList("attributes." + entry.getValue() + ".money-costs").getFirst());
            assertFalse(migrator.migrateSkillTree(yaml, entry.getKey(), 16));
            for (String change : List.of("values: [0.5]", "money-costs: [2000]", "skill-point-cost: 2",
                    "effect: water_breathing", "max-level: 2", "max-level: 1.5",
                    "skill-point-cost: 1.5", "category: custom")) {
                var custom = new YamlConfiguration();
                custom.loadFromString(original);
                String[] pair = change.split(": ", 2);
                var changed = new YamlConfiguration();
                changed.loadFromString(change);
                custom.set("attributes.fire_resistance." + pair[0], changed.get(pair[0]));
                String before = custom.saveToString();
                assertFalse(migrator.migrateSkillTree(custom, entry.getKey(), 16));
                assertEquals(before, custom.saveToString());
            }
            yaml.loadFromString(original);
            yaml.set("attributes.speed", "invalid");
            assertTrue(migrator.migrateSkillTree(yaml, entry.getKey(), 16));
            assertEquals("invalid", yaml.get("attributes.speed"));
            yaml.loadFromString(original);
            assertFalse(migrator.migrateSkillTree(yaml, entry.getKey(), 17));
            assertFalse(migrator.migrateSkillTree(yaml, "parrot", 16));
            yaml.set("attributes." + entry.getValue(), java.util.Map.of("effect", "armor"));
            assertFalse(migrator.migrateSkillTree(yaml, entry.getKey(), 16));
        }
    }

    @Test
    void versionElevenAddsAbilityFlagsAndPreservesExplicitChoices() {
        var configuration = new YamlConfiguration();
        configuration.set("config-version", 11);
        configuration.set("abilities.vigia.enabled", false);
        new ConfigurationMigrator().migrate(configuration, 11);
        assertEquals(ConfigurationMigrator.CURRENT_VERSION, configuration.getInt("config-version"));
        assertTrue(configuration.getBoolean("abilities.zarpazo.enabled"));
        assertFalse(configuration.getBoolean("abilities.vigia.enabled"));
        assertTrue(configuration.getBoolean("abilities.buscatesoros.enabled"));
        assertTrue(configuration.getBoolean("abilities.aterrizaje-felino.enabled"));
    }

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

        assertThrows(IllegalArgumentException.class, () -> migrator.migrate(configuration, ConfigurationMigrator.CURRENT_VERSION + 1));
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

    @Test
    void versionEightAddsMissingAbilityDefaultsWithoutOverwritingCustomValues() {
        var configuration = new YamlConfiguration();
        configuration.set("config-version", 7);
        configuration.set("abilities.zarpazo.radius", 9.5);

        assertTrue(new ConfigurationMigrator().migrate(configuration, 7));

        assertEquals(ConfigurationMigrator.CURRENT_VERSION, configuration.getInt("config-version"));
        assertEquals(9.5, configuration.getDouble("abilities.zarpazo.radius"));
        assertEquals(10, configuration.getInt("abilities.zarpazo.cooldown-seconds"));
        assertEquals(4.0, configuration.getDouble("abilities.zarpazo.damage"));
        assertEquals(5, configuration.getInt("abilities.vigia.cooldown-seconds"));
        assertEquals(16.0, configuration.getDouble("abilities.vigia.radius"));
        assertEquals(5, configuration.getInt("abilities.vigia.duration-seconds"));
    }

    @Test
    void versionNineAddsSoulItemDefaultsWithoutOverwritingCustomValues() {
        var configuration = new YamlConfiguration();
        configuration.set("config-version", 8);
        configuration.set("death.soul-item.material", "NETHER_STAR");

        assertTrue(new ConfigurationMigrator().migrate(configuration, 8));

        assertEquals(ConfigurationMigrator.CURRENT_VERSION, configuration.getInt("config-version"));
        assertEquals("NETHER_STAR", configuration.getString("death.soul-item.material"));
        assertEquals("<light_purple>Alma de <pet></light_purple>", configuration.getString("death.soul-item.name"));
        assertEquals(2, configuration.getStringList("death.soul-item.lore").size());
    }

    @Test
    void versionTenAddsTreasureDefaultsWithoutOverwritingCustomValues() {
        var configuration = new YamlConfiguration();
        configuration.set("config-version", 9);
        configuration.set("abilities.buscatesoros.max-targets", 7);

        assertTrue(new ConfigurationMigrator().migrate(configuration, 9));

        assertEquals(ConfigurationMigrator.CURRENT_VERSION, configuration.getInt("config-version"));
        assertEquals(30, configuration.getInt("abilities.buscatesoros.cooldown-seconds"));
        assertEquals(16.0, configuration.getDouble("abilities.buscatesoros.base-radius"));
        assertEquals(5, configuration.getInt("abilities.buscatesoros.duration-seconds"));
        assertEquals(7, configuration.getInt("abilities.buscatesoros.max-targets"));
        assertEquals(8192, configuration.getInt("abilities.buscatesoros.max-scan-blocks"));
        assertTrue(configuration.getStringList("abilities.buscatesoros.ore-materials")
                .contains("ANCIENT_DEBRIS"));
    }
}
