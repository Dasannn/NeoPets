package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Emitted after a pet's skill-tree progress has been successfully reset. */
public record TreeReset(PetId petId) implements DomainEvent {
    public TreeReset {
        Objects.requireNonNull(petId, "petId");
    }
}
