package dev.dasannn.neopets.plugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main entry point for NeoPets.
 */
public final class NeoPetsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("NeoPets enabled (version " + getPluginMeta().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        getLogger().info("NeoPets disabled");
    }
}
