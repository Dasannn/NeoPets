package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable request to deliver one issued soul token to its owner. */
public record PendingSoulDelivery(UUID tokenId, PetId petId, PlayerId ownerId, Instant createdAt) {
    public PendingSoulDelivery {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Creates a pending delivery from a newly issued token. */
    public static PendingSoulDelivery from(SoulToken token) {
        Objects.requireNonNull(token, "token");
        return new PendingSoulDelivery(token.tokenId(), token.petId(), token.ownerId(), token.issuedAt());
    }
}
