package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Emitted when a skill-tree node is purchased for a pet. */
public record NodeUnlocked(PetId petId, String attributeId, int level) implements DomainEvent {
    public NodeUnlocked {
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
