package dev.dasannn.neopets.plugin;

import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.plugin.integration.EconomyProvider;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for NeoPets.
 */
public final class NeoPetsPlugin extends JavaPlugin {
    private Economy economy;

    @Override
    public void onEnable() {
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
}
