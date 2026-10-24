package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PetProtectionListenerTest {
    private final PetProtectionListener listener = new PetProtectionListener();
    private final UUID ownerId = UUID.randomUUID();
    private final LivingEntity pet = taggedPet(ownerId);

    @Test
    void ownerMayInteractWithPet() {
        var event = new PlayerInteractEntityEvent(player(ownerId), pet, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void thirdPartyMayNotInteractWithPet() {
        var event = new PlayerInteractEntityEvent(player(UUID.randomUUID()), pet, EquipmentSlot.HAND);

        listener.onInteract(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void thirdPartyMayNotLeashPet() {
        var other = player(UUID.randomUUID());
        var event = new PlayerLeashEntityEvent(pet, other, other, EquipmentSlot.HAND);

        listener.onLeash(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void thirdPartyMayNotMountPet() {
        var event = new EntityMountEvent(player(UUID.randomUUID()), pet);

        listener.onMount(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void petMayNotTargetOwner() {
        var event = new EntityTargetLivingEntityEvent(pet, player(ownerId), TargetReason.CUSTOM);

        listener.onTarget(event);

        assertTrue(event.isCancelled());
        assertNull(event.getTarget());
    }

    @Test
    @SuppressWarnings("removal")
    void directOwnerDamageIsCancelled() {
        var owner = player(ownerId);
        var event = new EntityDamageByEntityEvent(
                owner, pet, DamageCause.ENTITY_ATTACK, damageSource(owner), 2.0);

        listener.onDamage(event);

        assertTrue(event.isCancelled());
    }

    @Test
    @SuppressWarnings("removal")
    void ownerProjectileDamageIsCancelled() {
        var owner = player(ownerId);
        var arrow = projectile(owner);
        var event = new EntityDamageByEntityEvent(
                arrow, pet, DamageCause.PROJECTILE, damageSource(null), 2.0);

        listener.onDamage(event);

        assertTrue(event.isCancelled());
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "toString" -> "test player";
                    default -> throw new AssertionError("Unexpected player call: " + method);
                });
    }

    private static Projectile projectile(Player shooter) {
        return (Projectile) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {Projectile.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getShooter" -> shooter;
                    case "toString" -> "test projectile";
                    default -> throw new AssertionError("Unexpected projectile call: " + method);
                });
    }

    private static DamageSource damageSource(Entity causingEntity) {
        return (DamageSource) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {DamageSource.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getCausingEntity" -> causingEntity;
                    case "toString" -> "test damage source";
                    default -> throw new AssertionError("Unexpected damage source call: " + method);
                });
    }

    private static LivingEntity taggedPet(UUID ownerId) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> true;
                    case "get" -> arguments[1] == PersistentDataType.STRING ? ownerId.toString() : null;
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        return (LivingEntity) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "toString" -> "test pet";
                    default -> throw new AssertionError("Unexpected pet call: " + method);
                });
    }
}
