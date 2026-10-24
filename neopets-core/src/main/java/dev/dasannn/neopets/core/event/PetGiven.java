package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.Objects;

/** Emitted when a player receives a pet. */
public record PetGiven(PetId petId, PlayerId ownerId, VariantId variantId) implements DomainEvent {
    public PetGiven {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(variantId, "variantId");
    }
}
