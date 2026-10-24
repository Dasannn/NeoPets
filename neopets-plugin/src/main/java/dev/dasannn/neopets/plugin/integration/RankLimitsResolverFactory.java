package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.util.Objects;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Selects and constructs the rank-limits integration during plugin enable. */
public final class RankLimitsResolverFactory {
    private RankLimitsResolverFactory() {
    }

    /**
     * Creates a resolver using LuckPerms when available, otherwise Bukkit permissions.
     *
     * <p>The Bukkit fallback captures permissions on the server thread and serves immutable
     * snapshots to asynchronous continuations.
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
            var bukkitPermissions = new BukkitPermissionRankSource(
                    plugin.getServer(), plugin.getServer()::isPrimaryThread,
                    () -> configurationManager.current().settings().ranks().keySet());
            plugin.getServer().getPluginManager().registerEvents(bukkitPermissions, plugin);
            long refreshTicks = configurationManager.current().settings()
                    .bukkitRankRefreshInterval().toSeconds() * 20L;
            plugin.getServer().getScheduler().runTaskTimer(
                    plugin, bukkitPermissions::refreshOnlinePlayers, refreshTicks, refreshTicks);
            rankSource = bukkitPermissions;
            plugin.getLogger().warning(
                    "LuckPerms is unavailable; rank limits strategy: Bukkit permissions "
                            + "snapshotted on the server thread (refresh every "
                            + configurationManager.current().settings()
                                    .bukkitRankRefreshInterval().toSeconds() + "s)");
        }

        return new ConfiguredRankLimitsResolver(
                () -> configurationManager.current().settings().ranks(), rankSource);
    }
}
