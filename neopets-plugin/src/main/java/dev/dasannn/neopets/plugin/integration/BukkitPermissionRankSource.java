package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.bukkit.Server;

final class BukkitPermissionRankSource implements RankSource {
    private static final String RANK_PERMISSION_PREFIX = "neopets.rank.";

    private final Server server;
    private final Executor mainThreadExecutor;
    private final BooleanSupplier isMainThread;

    BukkitPermissionRankSource(
            Server server,
            Executor mainThreadExecutor,
            BooleanSupplier isMainThread
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.isMainThread = Objects.requireNonNull(isMainThread, "isMainThread");
    }

    @Override
    public Set<String> findRankIds(PlayerId playerId, Set<String> configuredRankIds) {
        return callOnMainThread(() -> findRankIdsOnMainThread(playerId, configuredRankIds));
    }

    private Set<String> findRankIdsOnMainThread(PlayerId playerId, Set<String> configuredRankIds) {
        var player = server.getPlayer(playerId.value());
        if (player == null) {
            return Set.of();
        }

        var heldRanks = new HashSet<String>();
        configuredRankIds.stream()
                .filter(rankId -> player.hasPermission(RANK_PERMISSION_PREFIX + rankId))
                .forEach(heldRanks::add);
        return Set.copyOf(heldRanks);
    }

    private <T> T callOnMainThread(Supplier<T> query) {
        if (isMainThread.getAsBoolean()) {
            return query.get();
        }
        return CompletableFuture.supplyAsync(query, mainThreadExecutor).join();
    }
}
