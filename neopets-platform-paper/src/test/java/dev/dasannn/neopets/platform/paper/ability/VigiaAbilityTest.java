package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class VigiaAbilityTest {
    @Test
    void activeRangeLevelsExtendDetectionAndResetRestoresBaseRadius() {
        var world = world();
        var near = hostile(world, UUID.randomUUID(), 16);
        var middle = hostile(world, UUID.randomUUID(), 20);
        var far = hostile(world, UUID.randomUUID(), 36);
        var outside = hostile(world, UUID.randomUUID(), 37);
        var nearby = new AtomicReference<List<Entity>>(List.of(near, middle, far, outside));
        var radii = new ArrayList<Double>();
        var owner = owner(world, UUID.randomUUID(), nearby, radii);
        var pet = pet(UUID.randomUUID());
        var petId = new PetId(UUID.randomUUID());
        var cooldowns = new CooldownTracker();
        var alerts = new ArrayList<List<LivingEntity>>();
        var ability = new VigiaAbility(
                () -> new VigiaAbility.Settings(Duration.ofSeconds(5), 16, Duration.ofSeconds(5)),
                (ignored, targets, duration) -> alerts.add(targets));
        double[] bonuses = {0, 4, 8, 12, 16, 20, 0, 4};
        for (int index = 0; index < bonuses.length; index++) {
            ability.tick(new AbilityContext(petId, pet, owner, 1, Instant.EPOCH.plusSeconds(5L * index),
                    cooldowns, bonuses[index], ability.id()));
        }
        assertEquals(List.of(List.of(near), List.of(middle), List.of(far), List.of(middle)), alerts);
        assertEquals(List.of(16.0, 20.0, 24.0, 28.0, 32.0, 36.0, 16.0, 20.0), radii);
    }

    @Test
    void disablingOnReloadStopsAlertsAndGlowWithoutResummoning() {
        var world = world();
        var first = hostile(world, UUID.randomUUID());
        var second = hostile(world, UUID.randomUUID());
        var nearby = new AtomicReference<List<Entity>>(List.of(first));
        var owner = owner(world, UUID.randomUUID(), nearby);
        var pet = pet(UUID.randomUUID());
        var petId = new PetId(UUID.randomUUID());
        var cooldowns = new CooldownTracker();
        var enabled = new AtomicBoolean(true);
        var alerts = new ArrayList<List<LivingEntity>>();
        var ability = new VigiaAbility(
                () -> new VigiaAbility.Settings(enabled.get(), Duration.ofSeconds(5), 16, Duration.ofSeconds(5)),
                (ignored, targets, duration) -> alerts.add(targets));
        ability.onActivate(pet, owner, 1);
        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH));
        enabled.set(false);
        nearby.set(List.of(first, second));
        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH.plusSeconds(5)));
        assertEquals(List.of(List.of(first)), alerts);
        enabled.set(true);
        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH.plusSeconds(5)));
        assertEquals(List.of(List.of(first), List.of(second)), alerts);
    }

    @Test
    void alertsAndGlowsOnlyNewTargetsUntilTheyLeaveTheScan() {
        var world = world();
        var first = hostile(world, UUID.randomUUID());
        var second = hostile(world, UUID.randomUUID());
        var nearby = new AtomicReference<List<Entity>>(List.of(first));
        var owner = owner(world, UUID.randomUUID(), nearby);
        var pet = pet(UUID.randomUUID());
        var petId = new PetId(UUID.randomUUID());
        var cooldowns = new CooldownTracker();
        var alertedTargets = new ArrayList<List<LivingEntity>>();
        var durations = new ArrayList<Duration>();
        var ability = new VigiaAbility(
                () -> new VigiaAbility.Settings(Duration.ofSeconds(5), 16, Duration.ofSeconds(5)),
                (ignoredOwner, targets, duration) -> {
                    alertedTargets.add(targets);
                    durations.add(duration);
                });

        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH));
        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH.plusSeconds(5)));
        nearby.set(List.of(first, second));
        ability.tick(context(ability, petId, pet, owner, cooldowns, Instant.EPOCH.plusSeconds(10)));

        assertEquals(List.of(List.of(first), List.of(second)), alertedTargets);
        assertEquals(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5)), durations);
    }

    private static AbilityContext context(
            VigiaAbility ability,
            PetId petId,
            LivingEntity pet,
            Player owner,
            CooldownTracker cooldowns,
            Instant now
    ) {
        return new AbilityContext(petId, pet, owner, 1, now, cooldowns, 0, ability.id());
    }

    private static Player owner(
            World world,
            UUID id,
            AtomicReference<List<Entity>> nearby
    ) {
        return owner(world, id, nearby, new ArrayList<>());
    }

    private static Player owner(World world, UUID id, AtomicReference<List<Entity>> nearby, List<Double> radii) {
        return (Player) Proxy.newProxyInstance(
                VigiaAbilityTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getLocation" -> new Location(world, 0, 0, 0);
                    case "getNearbyEntities" -> {
                        assertEquals(arguments[0], arguments[1]);
                        assertEquals(arguments[0], arguments[2]);
                        radii.add((Double) arguments[0]);
                        yield nearby.get();
                    }
                    case "toString" -> "test owner";
                    default -> throw new AssertionError("Unexpected owner call: " + method);
                });
    }

    private static LivingEntity pet(UUID id) {
        return (LivingEntity) Proxy.newProxyInstance(
                VigiaAbilityTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "toString" -> "test pet";
                    default -> throw new AssertionError("Unexpected pet call: " + method);
                });
    }

    private static Monster hostile(
            World world,
            UUID id
    ) {
        return hostile(world, id, 3);
    }

    private static Monster hostile(World world, UUID id, double distance) {
        PersistentDataContainer data = emptyPersistentData();
        return (Monster) Proxy.newProxyInstance(
                VigiaAbilityTest.class.getClassLoader(),
                new Class<?>[] {Monster.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getPersistentDataContainer" -> data;
                    case "getWorld" -> world;
                    case "getLocation" -> new Location(world, distance, 0, 0);
                    case "toString" -> "test hostile";
                    default -> throw new AssertionError("Unexpected hostile call: " + method);
                });
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(
                VigiaAbilityTest.class.getClassLoader(),
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
                VigiaAbilityTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> false;
                    case "toString" -> "empty persistent data";
                    default -> throw new AssertionError("Unexpected persistent-data call: " + method);
                });
    }
}
