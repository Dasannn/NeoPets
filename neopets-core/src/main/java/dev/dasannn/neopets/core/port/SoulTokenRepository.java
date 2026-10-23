package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asynchronous, single-use persistence for soul-item tokens. */
public interface SoulTokenRepository {
    /**
     * Stores a newly issued token.
     *
     * @return a future completed after the token is durable, or completed exceptionally if the identifier already
     *         exists or persistence fails
     */
    CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt);

    /** Returns whether the token exists, is unconsumed, and matches the supplied pet and owner. */
    CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId);

    /**
     * Atomically consumes a matching token.
     *
     * @return a future containing {@code true} only for the first successful consumption; {@code false} when the
     *         token is absent, already consumed, or does not match the supplied pet and owner
     */
    CompletableFuture<Boolean> consume(UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt);
}
