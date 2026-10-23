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

    /** Persists and returns a new token bound to one pet and its owner. */
    public CompletableFuture<SoulToken> issue(PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        var token = new SoulToken(
                Objects.requireNonNull(tokenIds.get(), "generated token id"), petId, ownerId, clock.instant());
        return tokens.issue(token.tokenId(), petId, ownerId, token.issuedAt()).thenApply(ignored -> token);
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
}
