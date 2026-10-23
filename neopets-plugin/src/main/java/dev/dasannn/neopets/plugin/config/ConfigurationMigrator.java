package dev.dasannn.neopets.plugin.config;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;

/** Applies a contiguous migration chain to the current configuration version. */
public final class ConfigurationMigrator {
    /** Current bundled configuration schema. */
    public static final int CURRENT_VERSION = 4;
    private static final Map<String, List<ValueMigration>> SKILL_TREE_VALUE_MIGRATIONS = skillTreeValueMigrations();

    private final int currentVersion;
    private final Map<Integer, ConfigMigration> bySource;

    /** Creates the bundled migration chain. */
    public ConfigurationMigrator() {
        this(CURRENT_VERSION, List.of(new ConfigMigration() {
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
                if (!configuration.isSet("player-data.load-timeout-seconds")) {
                    configuration.set("player-data.load-timeout-seconds", 10);
                }
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 2;
            }

            @Override
            public int toVersion() {
                return 3;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 3 adds language keys only; ConfigurationLoader merges those defaults.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 3;
            }

            @Override
            public int toVersion() {
                return 4;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 4 changes vanilla skill-tree values from absolute targets to cumulative bonuses.
                // The related per-tree files are migrated by migrateSkillTree.
            }
        }));
    }

    public ConfigurationMigrator(int currentVersion, List<ConfigMigration> migrations) {
        if (currentVersion < 1) {
            throw new IllegalArgumentException("currentVersion must be positive");
        }
        Objects.requireNonNull(migrations, "migrations");
        this.currentVersion = currentVersion;
        var indexed = new HashMap<Integer, ConfigMigration>();
        for (ConfigMigration migration : migrations) {
            Objects.requireNonNull(migration, "migration");
            if (migration.toVersion() != migration.fromVersion() + 1) {
                throw new IllegalArgumentException("migrations must advance exactly one version");
            }
            if (indexed.putIfAbsent(migration.fromVersion(), migration) != null) {
                throw new IllegalArgumentException("duplicate migration source version: " + migration.fromVersion());
            }
        }
        bySource = Map.copyOf(indexed);
    }

    /** Returns the schema version understood by this loader. */
    public int currentVersion() {
        return currentVersion;
    }

    /** Migrates in memory and reports whether the document changed. */
    public boolean migrate(YamlConfiguration configuration, int sourceVersion) {
        Objects.requireNonNull(configuration, "configuration");
        if (sourceVersion > currentVersion) {
            throw new IllegalArgumentException("version " + sourceVersion
                    + " is newer than supported version " + currentVersion);
        }
        boolean changed = false;
        int version = sourceVersion;
        while (version < currentVersion) {
            ConfigMigration migration = bySource.get(version);
            if (migration == null) {
                throw new IllegalArgumentException("no migration is available from version " + version);
            }
            migration.apply(configuration);
            version = migration.toVersion();
            configuration.set("config-version", version);
            changed = true;
        }
        return changed;
    }

    /**
     * Rewrites only unchanged bundled skill-tree values whose meaning changed in version 4.
     * User-customized value arrays are deliberately preserved.
     */
    public boolean migrateSkillTree(YamlConfiguration configuration, String type, int sourceVersion) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(type, "type");
        if (sourceVersion < 1 || sourceVersion >= 4) {
            return false;
        }
        boolean changed = false;
        for (ValueMigration migration : SKILL_TREE_VALUE_MIGRATIONS.getOrDefault(type, List.of())) {
            if (sameNumbers(configuration.getList(migration.path()), migration.oldValues())) {
                configuration.set(migration.path(), migration.newValues());
                changed = true;
            }
        }
        return changed;
    }

    private static boolean sameNumbers(List<?> actual, List<? extends Number> expected) {
        if (actual == null || actual.size() != expected.size()) {
            return false;
        }
        for (int index = 0; index < actual.size(); index++) {
            if (!(actual.get(index) instanceof Number number)
                    || !sameNumber(number, expected.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameNumber(Number actual, Number expected) {
        try {
            return new BigDecimal(actual.toString()).compareTo(new BigDecimal(expected.toString())) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Map<String, List<ValueMigration>> skillTreeValueMigrations() {
        List<Number> oldHealth = List.of(22, 24, 26, 28, 30);
        List<Number> bearHealth = List.of(32, 34, 36, 38, 40);
        List<Number> healthBonus = List.of(2, 4, 6, 8, 10);
        List<Number> oldRegeneration = List.of(0.1, 0.2, 0.3, 0.4, 0.5);
        List<Number> regeneration = List.of(1, 2, 3, 4, 5);
        List<Number> oldSpeed = List.of(0.32, 0.34, 0.36, 0.38, 0.4);
        List<Number> bearSpeed = List.of(0.27, 0.29, 0.31, 0.33, 0.35);
        List<Number> flightSpeed = List.of(0.42, 0.46, 0.5, 0.54, 0.58);
        List<Number> speedBonus = List.of(0.05, 0.1, 0.15, 0.2, 0.25);
        List<Number> oldArmor = List.of(2, 4, 6, 8, 10);
        List<Number> armorBonus = List.of(1, 2, 3, 4, 5);
        List<Number> oldRange = List.of(8, 12, 16, 20, 24);
        List<Number> rangeBonus = List.of(4, 8, 12, 16, 20);
        List<Number> oldStrength = List.of(7, 8, 9, 10, 12);
        List<Number> strengthBonus = List.of(1, 2, 3, 4, 5);

        return Map.of(
                "cat", List.of(
                        values("health", oldHealth, healthBonus),
                        values("regeneration", oldRegeneration, regeneration),
                        values("agility", oldSpeed, speedBonus),
                        values("armor", oldArmor, armorBonus)),
                "dog", List.of(
                        values("health", oldHealth, healthBonus),
                        values("regeneration", oldRegeneration, regeneration),
                        values("speed", oldSpeed, speedBonus),
                        values("scent", oldRange, rangeBonus)),
                "donkey", List.of(
                        values("health", oldHealth, healthBonus),
                        values("regeneration", oldRegeneration, regeneration),
                        values("armor", oldArmor, armorBonus)),
                "parrot", List.of(
                        values("health", oldHealth, healthBonus),
                        values("regeneration", oldRegeneration, regeneration),
                        values("flight_speed", flightSpeed, speedBonus),
                        values("alert_range", oldRange, rangeBonus)),
                "polar_bear", List.of(
                        values("health", bearHealth, healthBonus),
                        values("regeneration", oldRegeneration, regeneration),
                        values("strength", oldStrength, strengthBonus),
                        values("speed", bearSpeed, speedBonus)));
    }

    private static ValueMigration values(String attribute, List<Number> oldValues, List<Number> newValues) {
        return new ValueMigration("attributes." + attribute + ".values", oldValues, newValues);
    }

    private record ValueMigration(String path, List<Number> oldValues, List<Number> newValues) {
    }
}
