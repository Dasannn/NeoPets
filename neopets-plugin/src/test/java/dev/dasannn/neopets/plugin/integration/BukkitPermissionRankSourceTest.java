package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

class BukkitPermissionRankSourceTest {
    private static final UUID PLAYER_UUID = UUID.fromString("ce0dd298-273f-49ac-a56d-861f0f77bd95");
    private static final PlayerId PLAYER_ID = new PlayerId(PLAYER_UUID);

    @Test
    void shutdownDrainDoesNotWaitForAQueuedServerThreadPermissionQuery() throws Exception {
        var mainThread = new AtomicBoolean(true);
        var livePermissionChecks = new AtomicInteger();
        Player player = playerWith(livePermissionChecks, "neopets.rank.vip");
        var source = new BukkitPermissionRankSource(
                serverWith(player), mainThread::get, () -> Set.of("vip", "vipplus"));
        source.onJoin(new PlayerJoinEvent(player, Component.text("joined")));
        livePermissionChecks.set(0);
        mainThread.set(false);
        var work = new RuntimeWorkTracker();

        CompletableFuture<Set<String>> command = work.submit(() -> CompletableFuture.supplyAsync(
                () -> source.findRankIds(PLAYER_ID, Set.of("vip", "vipplus"))));
        CompletableFuture<Void> shutdownDrain = work.stopAndDrain();

        assertEquals(Set.of("vip"), command.get(1, TimeUnit.SECONDS));
        shutdownDrain.get(Duration.ofSeconds(1).toMillis(), TimeUnit.MILLISECONDS);
        assertEquals(0, livePermissionChecks.get());
    }

    @Test
    void snapshotUsesHasPermissionAndMatchesMainThreadResolution() {
        var mainThread = new AtomicBoolean(true);
        var checks = new AtomicInteger();
        Player player = playerWith(checks, "neopets.rank.operator");
        var source = new BukkitPermissionRankSource(
                serverWith(player), mainThread::get, () -> Set.of("operator", "vip"));

        source.onJoin(new PlayerJoinEvent(player, Component.text("joined")));
        mainThread.set(false);
        Set<String> snapshot = source.findRankIds(PLAYER_ID, Set.of("operator", "vip"));
        mainThread.set(true);

        assertEquals(Set.of("operator"), snapshot);
        assertEquals(snapshot, source.findRankIds(PLAYER_ID, Set.of("operator", "vip")));
    }

    @Test
    void periodicRefreshPicksUpGrantAndRevocationForAsyncResolution() {
        var mainThread = new AtomicBoolean(true);
        var checks = new AtomicInteger();
        var grants = new AtomicReference<>(Set.of("neopets.rank.vip"));
        Player player = playerWith(checks, grants);
        var source = new BukkitPermissionRankSource(
                serverWith(player), mainThread::get, () -> Set.of("vip", "vipplus"));
        source.onJoin(new PlayerJoinEvent(player, Component.text("joined")));

        mainThread.set(false);
        assertEquals(Set.of("vip"), source.findRankIds(PLAYER_ID, Set.of("vip", "vipplus")));

        grants.set(Set.of("neopets.rank.vipplus"));
        mainThread.set(true);
        source.refreshOnlinePlayers();
        mainThread.set(false);

        assertEquals(Set.of("vipplus"), source.findRankIds(PLAYER_ID, Set.of("vip", "vipplus")));
    }

    private static Server serverWith(Player player) {
        return proxy(Server.class, (method, arguments) -> switch (method) {
            case "getPlayer" -> PLAYER_UUID.equals(arguments[0]) ? player : null;
            case "getOnlinePlayers" -> Set.of(player);
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static Player playerWith(AtomicInteger livePermissionChecks, String... grantedPermissions) {
        return playerWith(livePermissionChecks, new AtomicReference<>(Set.of(grantedPermissions)));
    }

    private static Player playerWith(
            AtomicInteger livePermissionChecks,
            AtomicReference<Set<String>> grantedPermissions
    ) {
        return proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> PLAYER_UUID;
            case "hasPermission" -> {
                livePermissionChecks.incrementAndGet();
                yield grantedPermissions.get().contains(arguments[0]);
            }
            case "getName" -> "SnapshotPlayer";
            default -> throw new UnsupportedOperationException(method);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (ignored, method, arguments) -> invocation.invoke(method.getName(), arguments));
        return type.cast(proxy);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments);
    }
}
