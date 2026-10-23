package dev.dasannn.neopets.plugin.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** One ordered in-memory migration between adjacent configuration schema versions. */
public interface ConfigMigration {
    /** Source schema version accepted by this migration. */
    int fromVersion();

    /** Target schema version produced by this migration. */
    int toVersion();

    /** Applies this migration to the loaded root configuration. */
    void apply(YamlConfiguration configuration);
}
