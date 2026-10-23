package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;

/** Emitted when a dead pet is revived. */
public record PetRevived(PetId petId, PlayerId ownerId) implements DomainEvent {
    public PetRevived {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
