package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;

/** Emitted when an owned pet dies. */
public record PetDied(PetId petId, PlayerId ownerId) implements DomainEvent {
    public PetDied {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
