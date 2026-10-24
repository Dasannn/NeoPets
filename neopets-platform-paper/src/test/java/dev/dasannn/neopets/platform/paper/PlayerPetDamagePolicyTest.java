package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PlayerPetDamagePolicyTest {
    @Test
    void defaultRuleOnlyDeniesOwnerDamage() {
        var owner = UUID.randomUUID();
        var otherPlayer = UUID.randomUUID();
        var policy = PlayerPetDamagePolicy.ownerCannotDamage();

        assertTrue(policy.denies(owner, owner));
        assertFalse(policy.denies(owner, otherPlayer));
    }

    @Test
    @SuppressWarnings("removal")
    void cancelsOwnerDamageFromIndirectDamageSource() {
        var owner = UUID.randomUUID();
        var player = player(owner);
        var tnt = entity();
        var pet = taggedPet(owner);
        var source = (DamageSource) Proxy.newProxyInstance(
                PlayerPetDamagePolicyTest.class.getClassLoader(),
                new Class<?>[] {DamageSource.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getCausingEntity" -> player;
                    case "toString" -> "owner-primed TNT damage";
                    default -> throw new AssertionError("Unexpected damage source call: " + method);
                });
        var event = new EntityDamageByEntityEvent(tnt, pet, DamageCause.ENTITY_EXPLOSION, source, 4.0);

        assertEquals(owner, PetProtectionListener.attackingPlayerId(tnt, source).orElseThrow());
        new PetProtectionListener().onDamage(event);
        assertTrue(event.isCancelled());
    }

    private static Player player(UUID playerId) {
        return (Player) Proxy.newProxyInstance(
                PlayerPetDamagePolicyTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "toString" -> "test player";
                    default -> throw new AssertionError("Unexpected player call: " + method);
                });
    }

    private static Entity entity() {
        return (Entity) Proxy.newProxyInstance(
                PlayerPetDamagePolicyTest.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "test TNT";
                    default -> throw new AssertionError("Unexpected entity call: " + method);
                });
    }

    private static Entity taggedPet(UUID ownerId) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                PlayerPetDamagePolicyTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> true;
                    case "get" -> arguments[1] == PersistentDataType.STRING ? ownerId.toString() : null;
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        return (Entity) Proxy.newProxyInstance(
                PlayerPetDamagePolicyTest.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "toString" -> "test pet";
                    default -> throw new AssertionError("Unexpected pet call: " + method);
                });
    }
}
