package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.util.Objects;
import java.util.concurrent.Executor;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Selects and constructs the rank-limits integration during plugin enable. */
public final class RankLimitsResolverFactory {
    private RankLimitsResolverFactory() {
    }

    /**
     * Creates a resolver using LuckPerms when available, otherwise Bukkit permissions.
     *
     * <p>The Bukkit fallback evaluates permission checks on the server thread through an
     * executor. This avoids reading Bukkit permission state asynchronously while keeping
     * permission changes visible immediately.
     */
    public static ConfiguredRankLimitsResolver create(
            JavaPlugin plugin,
            ConfigurationManager configurationManager
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configurationManager, "configurationManager");

        RankSource rankSource;
        if (plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) {
            rankSource = new LuckPermsRankSource(LuckPermsProvider.get());
            plugin.getLogger().info(
                    "Rank limits strategy: LuckPerms cached inherited groups and permissions");
        } else {
            Executor mainThreadExecutor = task -> plugin.getServer().getScheduler().runTask(plugin, task);
            rankSource = new BukkitPermissionRankSource(
                    plugin.getServer(), mainThreadExecutor, plugin.getServer()::isPrimaryThread);
            plugin.getLogger().warning(
                    "LuckPerms is unavailable; rank limits strategy: Bukkit permissions "
                            + "evaluated on the server thread");
        }

        return new ConfiguredRankLimitsResolver(
                () -> configurationManager.current().settings().ranks(), rankSource);
    }
}
