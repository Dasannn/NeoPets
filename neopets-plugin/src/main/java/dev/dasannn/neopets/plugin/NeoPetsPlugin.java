package dev.dasannn.neopets.plugin;

import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.integration.RankLimitsResolverFactory;
import java.io.IOException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for NeoPets.
 */
public final class NeoPetsPlugin extends JavaPlugin {
    private ConfigurationManager configurationManager;
    private RankLimitsResolver rankLimitsResolver;

    @Override
    public void onEnable() {
        try {
            configurationManager = new ConfigurationManager(new ConfigurationLoader(getDataFolder().toPath()));
        } catch (IOException | ConfigurationLoadException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        rankLimitsResolver = RankLimitsResolverFactory.create(this, configurationManager);
        getLogger().info("NeoPets enabled (version " + getPluginMeta().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("NeoPets disabled");
    }

    /** Returns the live configuration manager after the plugin has enabled successfully. */
    public ConfigurationManager configurationManager() {
        if (configurationManager == null) {
            throw new IllegalStateException("NeoPets configuration is not loaded");
        }
        return configurationManager;
    }

    /** Returns the configured rank-limits resolver after the plugin has enabled successfully. */
    public RankLimitsResolver rankLimitsResolver() {
        if (rankLimitsResolver == null) {
            throw new IllegalStateException("NeoPets rank limits resolver is not initialized");
        }
        return rankLimitsResolver;
    }
}
