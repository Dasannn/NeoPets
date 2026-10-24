package dev.dasannn.neopets.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OwnedPetTest {
    private final PetId petId = new PetId(UUID.randomUUID());
    private final PlayerId ownerId = new PlayerId(UUID.randomUUID());
    private final VariantId variantId = new VariantId("dog_brown");
    private final Instant createdAt = Instant.parse("2026-10-23T10:15:30Z");

    @Test
    void requiresAllReferenceFields() {
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(null, ownerId, variantId, Optional.empty(), PetStatus.ALIVE, false, createdAt));
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(petId, null, variantId, Optional.empty(), PetStatus.ALIVE, false, createdAt));
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(petId, ownerId, null, Optional.empty(), PetStatus.ALIVE, false, createdAt));
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(petId, ownerId, variantId, null, PetStatus.ALIVE, false, createdAt));
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(petId, ownerId, variantId, Optional.empty(), null, false, createdAt));
        assertThrows(NullPointerException.class,
                () -> new OwnedPet(petId, ownerId, variantId, Optional.empty(), PetStatus.ALIVE, false, null));
    }

    @Test
    void rejectsBlankCustomName() {
        assertThrows(IllegalArgumentException.class,
                () -> new OwnedPet(petId, ownerId, variantId, Optional.of(" "), PetStatus.ALIVE, false, createdAt));
    }

    @Test
    void withMethodsReturnChangedCopiesWithoutMutatingOriginal() {
        var original = pet(Optional.empty(), PetStatus.ALIVE, false);

        var named = original.withCustomName("Luna");
        var dead = named.withStatus(PetStatus.DEAD);
        var active = original.withActive(true);

        assertEquals(Optional.empty(), original.customName());
        assertEquals(PetStatus.ALIVE, original.status());
        assertFalse(original.active());
        assertEquals(Optional.of("Luna"), named.customName());
        assertEquals(PetStatus.DEAD, dead.status());
        assertTrue(active.active());
        assertEquals(Optional.empty(), named.withCustomName(null).customName());
    }

    private OwnedPet pet(Optional<String> customName, PetStatus status, boolean active) {
        return new OwnedPet(petId, ownerId, variantId, customName, status, active, createdAt);
    }
}
