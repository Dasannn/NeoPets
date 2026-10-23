package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class ZarpazoAbilityTest {
    @Test
    void damagesOnlyTargetsInsideSphericalRadiusAndAttributesDamageToPet() {
        var world = world();
        var damageCounts = new int[2];
        var damageSources = new Entity[2];
        var inside = hostile(world, new Location(world, 3, 0, 0), damageCounts, damageSources, 0);
        var outsideSphere = hostile(world, new Location(world, 5, 5, 0), damageCounts, damageSources, 1);
        var petId = new PetId(UUID.randomUUID());
        var owner = owner(UUID.randomUUID());
        var pet = pet(world, List.of(inside, outsideSphere));
        var ability = new ZarpazoAbility(
                () -> new ZarpazoAbility.Settings(Duration.ofSeconds(10), 6, 4),
                (ignoredPet, ignoredLocation) -> { });

        ability.tick(new AbilityContext(
                petId, pet, owner, 1, Instant.EPOCH, new CooldownTracker(), ability.id()));

        assertEquals(1, damageCounts[0]);
        assertEquals(0, damageCounts[1]);
        assertSame(pet, damageSources[0]);
    }

    private static LivingEntity pet(World world, List<? extends Entity> nearby) {
        UUID id = UUID.randomUUID();
        return (LivingEntity) Proxy.newProxyInstance(
                ZarpazoAbilityTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(world, 0, 0, 0);
                    case "getNearbyEntities" -> nearby;
                    case "getWorld" -> world;
                    case "toString" -> "test pet";
                    default -> throw new AssertionError("Unexpected pet call: " + method);
                });
    }

    private static Monster hostile(
            World world,
            Location location,
            int[] damageCounts,
            Entity[] damageSources,
            int index
    ) {
        UUID id = UUID.randomUUID();
        PersistentDataContainer data = emptyPersistentData();
        return (Monster) Proxy.newProxyInstance(
                ZarpazoAbilityTest.class.getClassLoader(),
                new Class<?>[] {Monster.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getPersistentDataContainer" -> data;
                    case "getWorld" -> world;
                    case "getLocation" -> location;
                    case "damage" -> {
                        damageCounts[index]++;
                        damageSources[index] = (Entity) arguments[1];
                        yield null;
                    }
                    case "toString" -> "test hostile";
                    default -> throw new AssertionError("Unexpected hostile call: " + method);
                });
    }

    private static Player owner(UUID id) {
        return (Player) Proxy.newProxyInstance(
                ZarpazoAbilityTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "test owner";
                    default -> throw new AssertionError("Unexpected owner call: " + method);
                });
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(
                ZarpazoAbilityTest.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test world";
                    default -> throw new AssertionError("Unexpected world call: " + method);
                });
    }

    private static PersistentDataContainer emptyPersistentData() {
        return (PersistentDataContainer) Proxy.newProxyInstance(
                ZarpazoAbilityTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> false;
                    case "toString" -> "empty persistent data";
                    default -> throw new AssertionError("Unexpected persistent-data call: " + method);
                });
    }
}
