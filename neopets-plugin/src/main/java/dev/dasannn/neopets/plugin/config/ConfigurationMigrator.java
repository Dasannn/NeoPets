package dev.dasannn.neopets.plugin.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Applies a contiguous migration chain to the current configuration version. */
public final class ConfigurationMigrator {
    /** Current bundled configuration schema. */
    public static final int CURRENT_VERSION = 4;

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
