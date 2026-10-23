package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Chunk;
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
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH, cooldowns, 0, ability.id()));
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(1), cooldowns, 0, ability.id()));
        assertEquals(32, particles[0]);
        assertEquals(1, notifications[0]);
        enabled.set(false);
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(2), cooldowns, 0, ability.id()));
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(30), cooldowns, 0, ability.id()));
        assertEquals(32, particles[0]);
        assertEquals(1, notifications[0]);
        enabled.set(true);
        ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(30), cooldowns, 0, ability.id()));
        assertEquals(2, notifications[0]);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(BuscatesorosAbilityTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
