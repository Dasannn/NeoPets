package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;

/** Emitted when a pet becomes active in the world. */
public record PetSummoned(PetId petId, PlayerId ownerId) implements DomainEvent {
    public PetSummoned {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
