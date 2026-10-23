package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Emitted when a player assigns a skill point to an unlocked node. */
public record NodeActivated(PetId petId, String attributeId, int level) implements DomainEvent {
    public NodeActivated {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(attributeId, "attributeId");
        if (attributeId.isBlank()) {
            throw new IllegalArgumentException("attributeId must not be blank");
        }
        if (level <= 0) {
            throw new IllegalArgumentException("level must be greater than zero");
        }
    }
}
