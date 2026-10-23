package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PetProtectionListenerTest {
    private final PetProtectionListener listener = new PetProtectionListener();
    private final UUID ownerId = UUID.randomUUID();
    private final Entity pet = taggedPet(ownerId);

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

    private static Entity taggedPet(UUID ownerId) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> true;
                    case "get" -> arguments[1] == PersistentDataType.STRING ? ownerId.toString() : null;
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        return (Entity) Proxy.newProxyInstance(
                PetProtectionListenerTest.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "toString" -> "test pet";
                    default -> throw new AssertionError("Unexpected pet call: " + method);
                });
    }
}
