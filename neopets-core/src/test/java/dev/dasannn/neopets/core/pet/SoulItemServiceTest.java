package dev.dasannn.neopets.core.pet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.SoulTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SoulItemServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");

    @Test
    void issuesABoundTokenThatOnlyTheFirstMatchingConsumptionCanUse() {
        var repository = new FakeSoulTokens();
        var tokenId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        var petId = new PetId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        var ownerId = new PlayerId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        var otherOwner = new PlayerId(UUID.fromString("10000000-0000-0000-0000-000000000002"));
        var service = new SoulItemService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);

        SoulToken token = service.issue(petId, ownerId).join();

        assertTrue(service.isUsable(tokenId, petId, ownerId).join());
        assertFalse(service.isUsable(tokenId, petId, otherOwner).join());
        assertTrue(service.consume(tokenId, petId, ownerId).join());
        assertFalse(service.consume(tokenId, petId, ownerId).join());
        assertFalse(service.isUsable(tokenId, petId, ownerId).join());
        org.junit.jupiter.api.Assertions.assertEquals(
                new SoulToken(tokenId, petId, ownerId, NOW), token);
    }

    private static final class FakeSoulTokens implements SoulTokenRepository {
        private final Map<UUID, StoredToken> tokens = new HashMap<>();

        @Override
        public CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) {
            tokens.put(tokenId, new StoredToken(petId, ownerId, false));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId) {
            StoredToken token = tokens.get(tokenId);
            return CompletableFuture.completedFuture(token != null && !token.consumed()
                    && token.petId().equals(petId) && token.ownerId().equals(ownerId));
        }

        @Override
        public CompletableFuture<Boolean> consume(
                UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt
        ) {
            boolean usable = isUsable(tokenId, petId, ownerId).join();
            if (usable) {
                tokens.computeIfPresent(tokenId, (ignored, token) ->
                        new StoredToken(token.petId(), token.ownerId(), true));
            }
            return CompletableFuture.completedFuture(usable);
        }
    }

    private record StoredToken(PetId petId, PlayerId ownerId, boolean consumed) {
    }
}
