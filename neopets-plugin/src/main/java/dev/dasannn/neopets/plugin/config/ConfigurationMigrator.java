package dev.dasannn.neopets.plugin.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.file.YamlConfiguration;

/** Applies a contiguous migration chain to the current configuration version. */
public final class ConfigurationMigrator {
    /** Current bundled configuration schema. */
    public static final int CURRENT_VERSION = 3;

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
}
