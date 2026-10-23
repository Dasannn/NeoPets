package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class BukkitPermissionRankSource implements RankSource, Listener {
    private static final String RANK_PERMISSION_PREFIX = "neopets.rank.";

    private final Server server;
    private final BooleanSupplier isMainThread;
    private final ConcurrentHashMap<PlayerId, Set<String>> snapshots = new ConcurrentHashMap<>();

    BukkitPermissionRankSource(Server server, BooleanSupplier isMainThread) {
        this.server = Objects.requireNonNull(server, "server");
        this.isMainThread = Objects.requireNonNull(isMainThread, "isMainThread");
    }

    @Override
    public Set<String> findRankIds(PlayerId playerId, Set<String> configuredRankIds) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(configuredRankIds, "configuredRankIds");
        if (isMainThread.getAsBoolean()) {
            return refresh(playerId, configuredRankIds);
        }
        Set<String> heldRanks = snapshots.getOrDefault(playerId, Set.of());
        var configuredSnapshot = new HashSet<>(heldRanks);
        configuredSnapshot.retainAll(configuredRankIds);
        return Set.copyOf(configuredSnapshot);
    }

    /** Captures Bukkit permission state before any asynchronous pet work can resolve rank limits. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        capture(event.getPlayer());
    }

    /** Refreshes permissions whose values can depend on the player's current world. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        capture(event.getPlayer());
    }

    /** Discards snapshots after their player has fully disconnected. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        snapshots.remove(new PlayerId(event.getPlayer().getUniqueId()));
    }

    private Set<String> refresh(PlayerId playerId, Set<String> configuredRankIds) {
        var player = server.getPlayer(playerId.value());
        if (player == null) {
            snapshots.remove(playerId);
            return Set.of();
        }

        var heldRanks = new HashSet<String>();
        configuredRankIds.stream()
                .filter(rankId -> player.hasPermission(RANK_PERMISSION_PREFIX + rankId))
                .forEach(heldRanks::add);
        Set<String> snapshot = Set.copyOf(heldRanks);
        snapshots.put(playerId, snapshot);
        return snapshot;
    }

    private void capture(Player player) {
        var heldRanks = new HashSet<String>();
        player.getEffectivePermissions().stream()
                .filter(permission -> permission.getValue()
                        && permission.getPermission().startsWith(RANK_PERMISSION_PREFIX))
                .map(permission -> permission.getPermission().substring(RANK_PERMISSION_PREFIX.length()))
                .forEach(heldRanks::add);
        snapshots.put(new PlayerId(player.getUniqueId()), Set.copyOf(heldRanks));
    }
}
