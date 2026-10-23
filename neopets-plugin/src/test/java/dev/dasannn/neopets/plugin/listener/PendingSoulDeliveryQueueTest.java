package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PendingSoulDeliveryRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.SoulTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PendingSoulDeliveryQueueTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final PlayerId OWNER = new PlayerId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PetId PET_ID = new PetId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final SoulToken TOKEN = new SoulToken(
            UUID.fromString("30000000-0000-0000-0000-000000000001"), PET_ID, OWNER, NOW);
    private static final OwnedPet PET = new OwnedPet(
            PET_ID, OWNER, new VariantId("dog_brown"), Optional.empty(), PetStatus.DEAD, false, NOW);

    @Test
    void offlineSoulRemainsPendingAndIsAcknowledgedOnNextDeliveryAttempt() {
        var fixture = new Fixture();
        var online = new AtomicBoolean();
        var received = new java.util.ArrayList<String>();
        var queue = fixture.queue((owner, item, pet) -> {
            if (!online.get()) {
                return CompletableFuture.completedFuture(false);
            }
            received.add(item);
            return CompletableFuture.completedFuture(true);
        });

        queue.enqueueAndDeliver(TOKEN).join();

        assertEquals(List.of(PendingSoulDelivery.from(TOKEN)), fixture.pending.findByOwner(OWNER).join());
        assertFalse(fixture.tokens.wasDelivered(TOKEN.tokenId(), PET_ID, OWNER).join());

        online.set(true);
        queue.deliverPending(OWNER).join();

        assertEquals(List.of("soul:" + TOKEN.tokenId()), received);
        assertEquals(List.of(), fixture.pending.findByOwner(OWNER).join());
        assertTrue(fixture.tokens.wasDelivered(TOKEN.tokenId(), PET_ID, OWNER).join());
    }

    @Test
    void overlappingEnqueueAndJoinDeliveryHandOutOnlyOneItem() {
        var fixture = new Fixture();
        var deliveryStarted = new CompletableFuture<Void>();
        var releaseDelivery = new CompletableFuture<Boolean>();
        var attempts = new AtomicInteger();
        var queue = fixture.queue((owner, item, pet) -> {
            assertTrue(fixture.pending.claimed.contains(TOKEN.tokenId()), "claim must precede the inventory write");
            attempts.incrementAndGet();
            deliveryStarted.complete(null);
            return releaseDelivery;
        });

        CompletableFuture<Void> enqueue = queue.enqueueAndDeliver(TOKEN);
        deliveryStarted.join();
        CompletableFuture<Void> join = queue.deliverPending(OWNER);

        assertFalse(enqueue.isDone());
        assertFalse(join.isDone());
        assertEquals(1, attempts.get());

        releaseDelivery.complete(true);
        enqueue.join();
        join.join();

        assertEquals(1, attempts.get());
        assertEquals(List.of(), fixture.pending.findByOwner(OWNER).join());
    }

    @Test
    void deletionFailureAfterAcknowledgmentNeverHandsOutAnotherItem() {
        var fixture = new Fixture();
        fixture.pending.failDelete = true;
        var attempts = new AtomicInteger();
        var queue = fixture.queue((owner, item, pet) -> {
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        });
        org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.CompletionException.class,
                () -> queue.enqueueAndDeliver(TOKEN).join());
        assertTrue(fixture.tokens.wasDelivered(TOKEN.tokenId(), PET_ID, OWNER).join());

        fixture.pending.failDelete = false;
        fixture.queue((owner, item, pet) -> {
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }).deliverPending(OWNER).join();

        assertEquals(1, attempts.get());
        assertEquals(List.of(), fixture.pending.findByOwner(OWNER).join());
    }

    @Test
    void restartRecoversAClaimLeftBeforeTheInventoryWrite() {
        var fixture = new Fixture();
        fixture.pending.save(PendingSoulDelivery.from(TOKEN)).join();
        assertTrue(fixture.pending.claim(TOKEN.tokenId()).join());
        var attempts = new AtomicInteger();

        fixture.queue((owner, item, pet) -> {
            assertTrue(fixture.pending.claimed.contains(TOKEN.tokenId()));
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        }).deliverPending(OWNER).join();

        assertEquals(1, attempts.get());
        assertTrue(fixture.tokens.wasDelivered(TOKEN.tokenId(), PET_ID, OWNER).join());
        assertEquals(List.of(), fixture.pending.findByOwner(OWNER).join());
    }

    @Test
    void joinDeliveryOwnsTheSequenceUntilPendingDeletionCompletes() {
        var fixture = new Fixture();
        fixture.pending.save(PendingSoulDelivery.from(TOKEN)).join();
        fixture.pending.delayedDelete = new CompletableFuture<>();
        var attempts = new AtomicInteger();
        var queue = fixture.queue((owner, item, pet) -> {
            attempts.incrementAndGet();
            return CompletableFuture.completedFuture(true);
        });

        var join = queue.deliverPending(OWNER);
        var enqueue = queue.enqueueAndDeliver(TOKEN);
        assertFalse(join.isDone());
        assertFalse(enqueue.isDone());
        assertEquals(1, attempts.get());

        fixture.pending.delayedDelete.complete(null);
        join.join();
        enqueue.join();
        assertEquals(1, attempts.get());
        assertEquals(List.of(), fixture.pending.findByOwner(OWNER).join());
    }

    private static final class Fixture {
        private final FakePending pending = new FakePending();
        private final FakeTokens tokens = new FakeTokens();
        private final SoulItemService souls = new SoulItemService(
                tokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> TOKEN.tokenId());

        private Fixture() {
            tokens.issue(TOKEN.tokenId(), PET_ID, OWNER, NOW).join();
        }

        private PendingSoulDeliveryQueue<String> queue(
                PendingSoulDeliveryQueue.DeliveryAttempt<String> attempt
        ) {
            return new PendingSoulDeliveryQueue<>(pending, new OnePetRepository(), souls,
                    new PlayerOperationQueue(),
                    (queued, pet) -> "soul:" + queued.tokenId(), attempt);
        }
    }

    private static final class FakePending implements PendingSoulDeliveryRepository {
        private final Map<UUID, PendingSoulDelivery> entries = new LinkedHashMap<>();
        private boolean failDelete;
        private CompletableFuture<Void> delayedDelete;
        private final java.util.Set<UUID> claimed = new java.util.HashSet<>();

        @Override
        public CompletableFuture<Boolean> claim(UUID tokenId) {
            return CompletableFuture.completedFuture(entries.containsKey(tokenId) && claimed.add(tokenId));
        }

        @Override
        public CompletableFuture<Void> releaseClaims(PlayerId ownerId) {
            entries.values().stream().filter(entry -> entry.ownerId().equals(ownerId))
                    .forEach(entry -> claimed.remove(entry.tokenId()));
            return CompletableFuture.completedFuture(null);
        }


        @Override
        public CompletableFuture<Void> save(PendingSoulDelivery delivery) {
            entries.put(delivery.tokenId(), delivery);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<PendingSoulDelivery>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(entries.values().stream()
                    .filter(entry -> entry.ownerId().equals(ownerId)).toList());
        }

        @Override
        public CompletableFuture<Void> delete(UUID tokenId) {
            if (failDelete) {
                return CompletableFuture.failedFuture(new IllegalStateException("delete unavailable"));
            }
            if (delayedDelete != null) {
                return delayedDelete.thenRun(() -> entries.remove(tokenId));
            }
            entries.remove(tokenId);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class OnePetRepository implements PetRepository {
        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            return CompletableFuture.completedFuture(PET_ID.equals(petId) ? Optional.of(PET) : Optional.empty());
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(OWNER.equals(ownerId) ? List.of(PET) : List.of());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet pet) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeTokens implements SoulTokenRepository {
        private final Map<UUID, State> entries = new HashMap<>();

        @Override
        public CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) {
            entries.put(tokenId, new State(new SoulToken(tokenId, petId, ownerId, issuedAt), false, false));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<SoulToken>> findUnconsumedByPet(PetId petId) {
            return CompletableFuture.completedFuture(entries.values().stream()
                    .filter(state -> state.token().petId().equals(petId) && !state.consumed())
                    .map(State::token).findFirst());
        }

        @Override
        public CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId) {
            State state = entries.get(tokenId);
            return CompletableFuture.completedFuture(matches(state, petId, ownerId) && !state.consumed());
        }

        @Override
        public CompletableFuture<Boolean> consume(
                UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt
        ) {
            State state = entries.get(tokenId);
            boolean changed = matches(state, petId, ownerId) && !state.consumed();
            if (changed) {
                entries.put(tokenId, new State(state.token(), true, state.delivered()));
            }
            return CompletableFuture.completedFuture(changed);
        }

        @Override
        public CompletableFuture<Boolean> wasDelivered(UUID tokenId, PetId petId, PlayerId ownerId) {
            State state = entries.get(tokenId);
            return CompletableFuture.completedFuture(matches(state, petId, ownerId) && state.delivered());
        }

        @Override
        public CompletableFuture<Boolean> markDelivered(
                UUID tokenId, PetId petId, PlayerId ownerId, Instant deliveredAt
        ) {
            State state = entries.get(tokenId);
            boolean changed = matches(state, petId, ownerId) && !state.delivered();
            if (changed) {
                entries.put(tokenId, new State(state.token(), state.consumed(), true));
            }
            return CompletableFuture.completedFuture(changed);
        }

        private static boolean matches(State state, PetId petId, PlayerId ownerId) {
            return state != null && state.token().petId().equals(petId) && state.token().ownerId().equals(ownerId);
        }
    }

    private record State(SoulToken token, boolean consumed, boolean delivered) {
    }
}
