package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BuscatesorosAbilityTest {
    @Test
    void disablingDuringAnActiveScanStopsParticlesAndNotifications() {
        var enabled = new AtomicBoolean(true);
        var notifications = new int[1];
        var particles = new int[1];
        Block ore = proxy(Block.class, (object, method, args) -> switch (method.getName()) {
            case "getType" -> Material.DIAMOND_ORE;
            default -> throw new AssertionError(method);
        });
        World world = proxy(World.class, (object, method, args) -> switch (method.getName()) {
            case "getLoadedChunks" -> new Chunk[0];
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> true;
            case "getBlockAt" -> ore;
            case "getChunkAt" -> proxy(Chunk.class, (chunk, chunkMethod, chunkArgs) -> {
                if (!chunkMethod.getName().equals("getChunkSnapshot")) {
                    throw new AssertionError(chunkMethod);
                }
                return proxy(ChunkSnapshot.class, (snapshot, snapshotMethod, coordinates) -> {
                    if (!snapshotMethod.getName().equals("getBlockType")) {
                        throw new AssertionError(snapshotMethod);
                    }
                    return Material.DIAMOND_ORE;
                });
            });
            default -> throw new AssertionError(method);
        });
        UUID entityId = UUID.randomUUID();
        LivingEntity pet = proxy(LivingEntity.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> entityId;
            case "getLocation" -> new Location(world, 0, 64, 0);
            default -> throw new AssertionError(method);
        });
        Player owner = proxy(Player.class, (object, method, args) -> {
            if (method.getName().equals("spawnParticle")) {
                particles[0]++;
                return null;
            }
            throw new AssertionError(method);
        });
        var ability = new BuscatesorosAbility(() -> new BuscatesorosAbility.Settings(
                enabled.get(), Duration.ofSeconds(30), 16, Duration.ofSeconds(5), 32, 8192,
                Set.of(Material.DIAMOND_ORE)), (player, count) -> notifications[0]++);
        var petId = new PetId(UUID.randomUUID());
        var cooldowns = new CooldownTracker();
        ability.onActivate(pet, owner, 1);
        // The scan is spread over ticks; advance until it has found targets and notified the owner.
        for (int tick = 0; tick < 200 && notifications[0] == 0; tick++) {
            ability.tick(new AbilityContext(
                    petId, pet, owner, 1, Instant.EPOCH.plusMillis(tick), cooldowns, 0, ability.id()));
        }
        assertEquals(1, notifications[0]);
        assertTrue(particles[0] > 0);
        int particlesBeforeDisable = particles[0];
        enabled.set(false);
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(2), cooldowns, 0, ability.id()));
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(30), cooldowns, 0, ability.id()));
        assertEquals(particlesBeforeDisable, particles[0]);
        assertEquals(1, notifications[0]);
        enabled.set(true);
        for (int tick = 0; tick < 200 && notifications[0] == 1; tick++) {
            ability.tick(new AbilityContext(
                    petId, pet, owner, 1, Instant.EPOCH.plusSeconds(30).plusMillis(tick), cooldowns, 0, ability.id()));
        }
        assertEquals(2, notifications[0]);
    }

    @Test
    void discoveriesEmitImmediatelyExpireIndividuallyAndNotifyOnceAtCompletion() {
        var fixture = new ScanFixture();
        fixture.tick(0);
        assertEquals(1, fixture.particles, "first target is marked while traversal is pending");
        assertEquals(0, fixture.notifications);
        fixture.tick(1);
        assertEquals(2, fixture.particles);
        fixture.tick(2);
        assertEquals(3, fixture.particles);
        // Nearby shells cross four chunks; the one-snapshot-per-tick limit delays this discovery.
        fixture.tick(3);
        assertEquals(5, fixture.particles, "second target is marked as soon as it is found");
        assertEquals(0, fixture.notifications, "notification waits for the remaining scan budget");
        fixture.tick(4);
        assertEquals(7, fixture.particles);
        assertEquals(1, fixture.notifications);
        fixture.tick(5);
        assertEquals(8, fixture.particles, "only the later discovery remains marked");
        fixture.tick(8);
        fixture.tick(29);
        assertEquals(8, fixture.particles, "each target expires five seconds after discovery");
        assertEquals(1, fixture.notifications);
        int snapshots = fixture.snapshots;
        fixture.tick(30);
        assertTrue(fixture.snapshots > snapshots, "cooldown is ready exactly at 30 seconds");
        fixture.finishScan(31);
        assertEquals(2, fixture.notifications);
    }

    @Test
    void deactivationDiscardsPendingTraversalAndActiveMarkers() {
        var fixture = new ScanFixture();
        fixture.tick(0);
        assertEquals(0, fixture.notifications);
        fixture.ability.onDeactivate(fixture.pet, fixture.owner, 1);
        int initialParticles = fixture.particles;
        fixture.tick(1);
        assertEquals(0, fixture.notifications);
        assertEquals(initialParticles, fixture.particles);
        fixture.finishScan(30);
        int particles = fixture.particles;
        fixture.ability.onDeactivate(fixture.pet, fixture.owner, 1);
        fixture.tick(35);
        assertEquals(1, fixture.notifications);
        assertEquals(particles, fixture.particles);
    }

    private static final class ScanFixture {
        private int notifications;
        private int particles;
        private int snapshots;
        private final PetId petId = new PetId(UUID.randomUUID());
        private final UUID entityId = UUID.randomUUID();
        private final CooldownTracker cooldowns = new CooldownTracker();
        private final World world = proxy(World.class, (object, method, args) -> switch (method.getName()) {
            case "getMinHeight" -> -64;
            case "getMaxHeight" -> 320;
            case "isChunkLoaded" -> true;
            case "getChunkAt" -> {
                int chunkX = (int) args[0];
                int chunkZ = (int) args[1];
                yield proxy(Chunk.class, (chunk, chunkMethod, chunkArgs) -> {
                    assertEquals("getChunkSnapshot", chunkMethod.getName());
                    snapshots++;
                    return proxy(ChunkSnapshot.class, (snapshot, snapshotMethod, coordinates) -> {
                        assertEquals("getBlockType", snapshotMethod.getName());
                        return chunkX == 0 && chunkZ == 0 && (int) coordinates[0] == 0
                                && (int) coordinates[1] == 64
                                && ((int) coordinates[2] == 0 || (int) coordinates[2] == 2)
                                ? Material.DIAMOND_ORE : Material.AIR;
                    });
                });
            }
            default -> throw new AssertionError(method);
        });
        private final LivingEntity pet = proxy(LivingEntity.class, (object, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> entityId;
            case "getLocation" -> new Location(world, 0, 64, 0);
            default -> throw new AssertionError(method);
        });
        private final Player owner = proxy(Player.class, (object, method, args) -> {
            assertEquals("spawnParticle", method.getName());
            particles++;
            return null;
        });
        private final BuscatesorosAbility ability = new BuscatesorosAbility(
                () -> new BuscatesorosAbility.Settings(Duration.ofSeconds(30), 16,
                        Duration.ofSeconds(5), 32, 1300, Set.of(Material.DIAMOND_ORE)),
                (player, count) -> {
                    assertSame(owner, player);
                    assertEquals(2, count);
                    notifications++;
                });

        private void tick(int seconds) {
            ability.tick(new AbilityContext(petId, pet, owner, 1,
                    Instant.EPOCH.plusSeconds(seconds), cooldowns, 0, ability.id()));
        }

        private int finishScan(int start) {
            int previous = notifications;
            for (int second = start; second < start + 20; second++) {
                tick(second);
                if (notifications > previous) {
                    return second;
                }
            }
            throw new AssertionError("scan did not finish");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(BuscatesorosAbilityTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
