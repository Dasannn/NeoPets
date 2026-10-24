package dev.dasannn.neopets.core.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable pet instance owned by a player.
 *
 * <p>State changes return a new value, making instances safe to pass between asynchronous
 * repositories and domain services.</p>
 */
public record OwnedPet(
        PetId id,
        PlayerId owner,
        VariantId variant,
        Optional<String> customName,
        PetStatus status,
        boolean active,
        Instant createdAt
) {
    public OwnedPet {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(customName, "customName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        customName.ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("customName must not be blank when present");
            }
        });
    }

    /** Returns a copy with the supplied custom name, or no name when {@code null}. */
    public OwnedPet withCustomName(String newCustomName) {
        return new OwnedPet(id, owner, variant, Optional.ofNullable(newCustomName), status, active, createdAt);
    }

    /** Returns a copy with a different lifecycle status. */
    public OwnedPet withStatus(PetStatus newStatus) {
        return new OwnedPet(id, owner, variant, customName, newStatus, active, createdAt);
    }

    /** Returns a copy with a different summoned state. */
    public OwnedPet withActive(boolean newActive) {
        return new OwnedPet(id, owner, variant, customName, status, newActive, createdAt);
    }
}
