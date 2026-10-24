package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class OrphanCleanerTest {
    @Test
    void removesMalformedTagAndContinuesCleaningSubsequentEntities() {
        var malformed = entityWithPetId("ignored", true, false);
        var orphan = entityWithPetId(UUID.randomUUID().toString(), true, true);
        var activeId = new PetId(UUID.randomUUID());
        var active = entityWithPetId(activeId.toString(), true, true);

        var removed = new OrphanCleaner().cleanEntities(
                List.of(malformed.entity(), orphan.entity(), active.entity()), Set.of(activeId));

        assertEquals(2, removed);
        assertTrue(malformed.removed());
        assertTrue(orphan.removed());
        assertFalse(active.removed());
    }

    private static EntityStub entityWithPetId(String petId, boolean hasKey, boolean hasString) {
        var removed = new boolean[1];
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                OrphanCleanerTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "has" -> arguments.length == 1 ? hasKey : hasKey && hasString;
                    case "get" -> {
                        if (!hasString || arguments[1] != PersistentDataType.STRING) {
                            throw new IllegalArgumentException("stored primitive type does not match");
                        }
                        yield petId;
                    }
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        var entity = (Entity) Proxy.newProxyInstance(
                OrphanCleanerTest.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "remove" -> {
                        removed[0] = true;
                        yield null;
                    }
                    case "toString" -> "test entity";
                    default -> throw new AssertionError("Unexpected entity call: " + method);
                });
        return new EntityStub(entity, removed);
    }

    private record EntityStub(Entity entity, boolean[] removalState) {
        boolean removed() {
            return removalState[0];
        }
    }
}
