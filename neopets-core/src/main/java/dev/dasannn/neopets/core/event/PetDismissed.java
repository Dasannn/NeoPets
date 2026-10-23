package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;

/** Emitted when a pet is removed from the world without dying. */
public record PetDismissed(PetId petId, PlayerId ownerId) implements DomainEvent {
    public PetDismissed {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
