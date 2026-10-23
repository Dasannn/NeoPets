package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asynchronous, single-use persistence for soul-item tokens. */
public interface SoulTokenRepository {
    CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt);

    CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId);

    /** Atomically consumes a token, returning {@code false} if it was absent or already consumed. */
    CompletableFuture<Boolean> consume(UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt);
}
