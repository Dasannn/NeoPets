package dev.dasannn.neopets.plugin.config;

import dev.dasannn.neopets.platform.paper.ability.BuscatesorosAbility;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Applies a contiguous migration chain to the current configuration version. */
public final class ConfigurationMigrator {
    /** Current bundled configuration schema. */
    public static final int CURRENT_VERSION = 16;
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
                // Version 4 repairs legacy pet resources; the root document needs only its version bump.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 4;
            }

            @Override
            public int toVersion() {
                return 5;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 5 changes vanilla skill-tree values from absolute targets to cumulative bonuses.
                // The related per-tree files are migrated by migrateSkillTree.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 5;
            }

            @Override
            public int toVersion() {
                return 6;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 6 adds skill-tree GUI language keys only.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 6;
            }

            @Override
            public int toVersion() {
                return 7;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 7 makes the fallback Bukkit permission refresh interval configurable.
                if (!configuration.isSet("rank-resolution.bukkit-refresh-interval-seconds")) {
                    configuration.set("rank-resolution.bukkit-refresh-interval-seconds", 30);
                }
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 7;
            }

            @Override
            public int toVersion() {
                return 8;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 8 adds the unique ability settings.
                setIfMissing(configuration, "abilities.zarpazo.cooldown-seconds", 10);
                setIfMissing(configuration, "abilities.zarpazo.radius", 6.0);
                setIfMissing(configuration, "abilities.zarpazo.damage", 4.0);
                setIfMissing(configuration, "abilities.vigia.cooldown-seconds", 5);
                setIfMissing(configuration, "abilities.vigia.radius", 16.0);
                setIfMissing(configuration, "abilities.vigia.duration-seconds", 5);
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 8;
            }

            @Override
            public int toVersion() {
                return 9;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 9 adds the configurable soul item.
                if (!configuration.isSet("death.soul-item.material")) {
                    configuration.set("death.soul-item.material", "ECHO_SHARD");
                }
                if (!configuration.isSet("death.soul-item.name")) {
                    configuration.set("death.soul-item.name", "<light_purple>Alma de <pet></light_purple>");
                }
                if (!configuration.isSet("death.soul-item.lore")) {
                    configuration.set("death.soul-item.lore", List.of(
                            "<gray>Tipo: <type></gray>",
                            "<gray>Úsala con una poción de revivir</gray>"));
                }
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 9;
            }

            @Override
            public int toVersion() {
                return 10;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 10 adds Buscatesoros settings and its language message.
                setIfMissing(configuration, "abilities.buscatesoros.cooldown-seconds", 30);
                setIfMissing(configuration, "abilities.buscatesoros.base-radius", 16.0);
                setIfMissing(configuration, "abilities.buscatesoros.duration-seconds", 5);
                setIfMissing(configuration, "abilities.buscatesoros.max-targets", 32);
                setIfMissing(configuration, "abilities.buscatesoros.max-scan-blocks", 8192);
                setIfMissing(configuration, "abilities.buscatesoros.ore-materials",
                        BuscatesorosAbility.defaultOreMaterials().stream()
                                .map(Enum::name)
                                .sorted()
                                .toList());
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 10;
            }

            @Override
            public int toVersion() {
                return 11;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 11 adds administration-menu language keys only.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 11;
            }

            @Override
            public int toVersion() {
                return 12;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 12 adds an enable flag per unique ability.
                for (String ability : List.of("zarpazo", "vigia", "buscatesoros", "aterrizaje-felino")) {
                    setIfMissing(configuration, "abilities." + ability + ".enabled", true);
                }
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 12;
            }

            @Override
            public int toVersion() {
                return 13;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 13 adds revive-command language keys only.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 13;
            }

            @Override
            public int toVersion() {
                return 14;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 14 adds courier GUI and clickable-request language keys only.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() {
                return 14;
            }

            @Override
            public int toVersion() {
                return 15;
            }

            @Override
            public void apply(YamlConfiguration configuration) {
                // Version 15 adds ability controls and readable admin value language keys.
            }
        }, new ConfigMigration() {
            @Override
            public int fromVersion() { return 15; }

            @Override
            public int toVersion() { return 16; }

            @Override
            public void apply(YamlConfiguration configuration) {
                setIfMissing(configuration, "give.all-owned-command", "");
            }
        }));
    }

    private static void setIfMissing(YamlConfiguration configuration, String path, Object value) {
        if (!configuration.isSet(path)) {
            configuration.set(path, value);
        }
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
     * Rewrites only unchanged bundled skill-tree values whose meaning changed in version 5.
     * User-customized value arrays are deliberately preserved.
     */
    public boolean migrateSkillTree(YamlConfiguration configuration, String type, int sourceVersion) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(type, "type");
        if (currentVersion < 5 || sourceVersion < 1 || sourceVersion >= 5) {
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

    /** Migrates one pet document using the root configuration's original schema version. */
    public boolean migratePet(YamlConfiguration configuration, String petType, int sourceVersion) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(petType, "petType");
        if (currentVersion < 4 || sourceVersion >= 4) {
            return false;
        }

        ConfigurationSection variants = configuration.getConfigurationSection("variants");
        if (variants == null) {
            return false;
        }
        return switch (petType) {
            case "cat" -> migrateCatAppearances(configuration, variants);
            case "parrot" -> migrateParrotAppearances(configuration, variants);
            default -> false;
        };
    }

    private static boolean migrateCatAppearances(
            YamlConfiguration configuration, ConfigurationSection variants
    ) {
        boolean changed = false;
        for (String variantId : variants.getKeys(false)) {
            String appearance = "variants." + variantId + ".appearance";
            String legacyPath = appearance + ".variant";
            String currentPath = appearance + ".type";
            if (configuration.isSet(legacyPath)) {
                if (!configuration.isSet(currentPath)) {
                    configuration.set(currentPath, configuration.get(legacyPath));
                }
                configuration.set(legacyPath, null);
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

    private static boolean migrateParrotAppearances(
            YamlConfiguration configuration, ConfigurationSection variants
    ) {
        boolean changed = false;
        for (String variantId : variants.getKeys(false)) {
            String variantPath = "variants." + variantId + ".appearance.variant";
            String legacyVariant = configuration.getString(variantPath);
            String currentVariant = switch (legacyVariant == null ? "" : legacyVariant) {
                case "red_blue" -> "red";
                case "yellow_blue" -> "cyan";
                default -> null;
            };
            if (currentVariant != null) {
                configuration.set(variantPath, currentVariant);
                changed = true;
            }
        }

        for (String legacyBaby : List.of("parrot_red_blue_baby", "parrot_green_baby")) {
            String babyPath = "variants." + legacyBaby + ".baby";
            if (configuration.getBoolean(babyPath, false)) {
                configuration.set(babyPath, false);
                changed = true;
            }
        }
        return changed;
    }
}
