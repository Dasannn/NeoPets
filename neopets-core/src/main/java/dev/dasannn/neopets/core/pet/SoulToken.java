package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable identity stored in a soul item and validated when the pet is revived. */
public record SoulToken(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) {
    public SoulToken {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }
}
