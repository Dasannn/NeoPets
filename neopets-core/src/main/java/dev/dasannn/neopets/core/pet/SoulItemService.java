package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.SoulTokenRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Issues, validates, and atomically consumes the unique tokens represented by soul items. */
public final class SoulItemService {
    private final SoulTokenRepository tokens;
    private final Clock clock;
    private final Supplier<UUID> tokenIds;

    /** Creates a service that issues random UUID token identifiers. */
    public SoulItemService(SoulTokenRepository tokens, Clock clock) {
        this(tokens, clock, UUID::randomUUID);
    }

    /** Creates a service with an injectable token identifier source. */
    public SoulItemService(SoulTokenRepository tokens, Clock clock, Supplier<UUID> tokenIds) {
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokenIds = Objects.requireNonNull(tokenIds, "tokenIds");
    }

    /** Returns the existing unconsumed token for a pet, or durably issues one. */
    public CompletableFuture<SoulToken> issue(PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        return tokens.findUnconsumedByPet(petId).thenCompose(existing -> {
            if (existing.isPresent()) {
                SoulToken token = existing.orElseThrow();
                if (!token.ownerId().equals(ownerId)) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "existing soul token belongs to a different owner"));
                }
                return CompletableFuture.completedFuture(token);
            }
            var token = new SoulToken(
                    Objects.requireNonNull(tokenIds.get(), "generated token id"), petId, ownerId, clock.instant());
            return tokens.issue(token.tokenId(), petId, ownerId, token.issuedAt())
                    .handle((ignored, failure) -> failure == null
                            ? CompletableFuture.completedFuture(token)
                            : recoverConcurrentIssue(petId, ownerId, failure))
                    .thenCompose(result -> result);
        });
    }

    /** Returns whether the soul item represented by a token has reached its owner. */
    public CompletableFuture<Boolean> wasDelivered(SoulToken token) {
        Objects.requireNonNull(token, "token");
        return tokens.wasDelivered(token.tokenId(), token.petId(), token.ownerId());
    }

    /** Atomically acknowledges the first successful delivery of a soul item. */
    public CompletableFuture<Boolean> markDelivered(SoulToken token) {
        Objects.requireNonNull(token, "token");
        return tokens.markDelivered(token.tokenId(), token.petId(), token.ownerId(), clock.instant());
    }

    /** Returns whether the supplied identity refers to a matching, unconsumed token. */
    public CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        return tokens.isUsable(tokenId, petId, ownerId);
    }

    /** Atomically consumes the token; only the first matching call can return {@code true}. */
    public CompletableFuture<Boolean> consume(UUID tokenId, PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        return tokens.consume(tokenId, petId, ownerId, clock.instant());
    }

    private CompletableFuture<SoulToken> recoverConcurrentIssue(
            PetId petId, PlayerId ownerId, Throwable issueFailure
    ) {
        return tokens.findUnconsumedByPet(petId).thenCompose(existing -> {
            if (existing.isEmpty()) {
                return CompletableFuture.failedFuture(issueFailure);
            }
            SoulToken token = existing.orElseThrow();
            if (!token.ownerId().equals(ownerId)) {
                return CompletableFuture.failedFuture(issueFailure);
            }
            return CompletableFuture.completedFuture(token);
        });
    }
}
