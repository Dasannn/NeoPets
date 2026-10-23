package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LivePetRegistryTest {
    private final LivePetRegistry registry = new LivePetRegistry();
    private final PetId petId = new PetId(UUID.randomUUID());

    @Test
    void registersReplacesAndUnregistersEntities() {
        var firstEntity = UUID.randomUUID();
        var secondEntity = UUID.randomUUID();

        assertTrue(registry.register(petId, firstEntity).isEmpty());
        assertEquals(firstEntity, registry.entityId(petId).orElseThrow());
        assertEquals(firstEntity, registry.register(petId, secondEntity).orElseThrow());
        assertEquals(secondEntity, registry.unregister(petId).orElseThrow());
        assertTrue(registry.entityId(petId).isEmpty());
    }

    @Test
    void conditionalRemovalDoesNotDeleteAReplacement() {
        var firstEntity = UUID.randomUUID();
        var replacement = UUID.randomUUID();
        registry.register(petId, replacement);

        assertFalse(registry.unregister(petId, firstEntity));
        assertEquals(replacement, registry.entityId(petId).orElseThrow());
        assertTrue(registry.unregister(petId, replacement));
    }

    @Test
    void snapshotIsImmutableAndUnaffectedByLaterUpdates() {
        registry.register(petId, UUID.randomUUID());
        var snapshot = registry.snapshot();

        registry.unregister(petId);

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put(new PetId(UUID.randomUUID()), UUID.randomUUID()));
    }

    @Test
    void rejectsNullIdentifiers() {
        assertThrows(NullPointerException.class, () -> registry.register(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> registry.register(petId, null));
        assertThrows(NullPointerException.class, () -> registry.entityId(null));
    }
}
