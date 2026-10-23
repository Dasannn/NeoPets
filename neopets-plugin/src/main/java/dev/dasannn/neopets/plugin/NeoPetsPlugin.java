package dev.dasannn.neopets.plugin;

import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.integration.EconomyProvider;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for NeoPets.
 */
public final class NeoPetsPlugin extends JavaPlugin {
    private ConfigurationManager configurationManager;
    private Economy economy;

    @Override
    public void onEnable() {
        try {
            configurationManager = new ConfigurationManager(new ConfigurationLoader(getDataFolder().toPath()));
        } catch (IOException | ConfigurationLoadException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Executor mainThreadExecutor = task -> {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                getServer().getScheduler().runTask(this, task);
            }
        };
        economy = EconomyProvider.resolve(getServer(), mainThreadExecutor, getLogger());
        getLogger().info("NeoPets enabled (version " + getPluginMeta().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        economy = null;
        getLogger().info("NeoPets disabled");
    }

    /** Returns the live configuration manager after the plugin has enabled successfully. */
    public ConfigurationManager configurationManager() {
        if (configurationManager == null) {
            throw new IllegalStateException("NeoPets configuration is not loaded");
        }
        return configurationManager;
    }
}
