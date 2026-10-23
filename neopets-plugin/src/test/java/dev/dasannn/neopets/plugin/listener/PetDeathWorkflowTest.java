package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.DeathResult;
import dev.dasannn.neopets.core.pet.PetDeathWork;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PetDeathWorkRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PetDeathWorkflowTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final PlayerId OWNER = new PlayerId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PetId PET_ID = new PetId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final UUID TOKEN_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void keepSkillsRetainsProgressAndStillCheckpointsAndQueuesTheSoul() {
        var fixture = new Fixture(true);
        fixture.assignedPoints.set(4);
        fixture.balance.set(2);

        fixture.workflow.start(OWNER, PET_ID).join();

        assertEquals(PetStatus.DEAD, fixture.pets.pet.status());
        assertEquals(4, fixture.assignedPoints.get());
        assertEquals(2, fixture.balance.get());
        assertEquals(0, fixture.resetCalls.get());
        assertTrue(fixture.work.entries.get(PET_ID).skillsHandled());
        assertEquals(List.of(TOKEN_ID), fixture.deliveredTokens);
    }

    @Test
    void resetPolicyZeroesProgressAndRefundsTheExactAssignedPointsOnce() {
        var fixture = new Fixture(false);
        fixture.assignedPoints.set(4);
        fixture.balance.set(2);

        fixture.workflow.start(OWNER, PET_ID).join();
        fixture.workflow.reconcile(OWNER).join();

        assertEquals(0, fixture.assignedPoints.get());
        assertEquals(6, fixture.balance.get());
        assertEquals(1, fixture.resetCalls.get());
        assertTrue(fixture.work.entries.get(PET_ID).skillsHandled());
    }

    @Test
    void restartReconciliationCompletesADeadPetAfterDeliveryEnqueueFailed() {
        var fixture = new Fixture(false);
        fixture.assignedPoints.set(3);
        fixture.balance.set(1);
        fixture.failFirstDelivery.set(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class, () -> fixture.workflow.start(OWNER, PET_ID).join());

        assertEquals(PetStatus.DEAD, fixture.pets.pet.status());
        assertEquals(4, fixture.balance.get());
        assertTrue(fixture.work.entries.get(PET_ID).skillsHandled());
        assertEquals(1, fixture.issuedTokens.size());

        PetDeathWorkflow restarted = fixture.newWorkflow();
        restarted.reconcile(OWNER).join();

        assertEquals(1, fixture.resetCalls.get(), "the persisted skill checkpoint must prevent a second refund");
        assertEquals(1, fixture.issuedTokens.size(), "token issuance must be idempotent by pet");
        assertEquals(List.of(TOKEN_ID), fixture.deliveredTokens);
    }

    private static final class Fixture {
        private final FakePets pets = new FakePets(alivePet());
        private final FakeDeathWork work = new FakeDeathWork();
        private final boolean keepSkills;
        private final AtomicInteger assignedPoints = new AtomicInteger();
        private final AtomicInteger balance = new AtomicInteger();
        private final AtomicInteger resetCalls = new AtomicInteger();
        private final AtomicBoolean failFirstDelivery = new AtomicBoolean();
        private final Map<PetId, SoulToken> issuedTokens = new HashMap<>();
        private final List<UUID> deliveredTokens = new ArrayList<>();
        private final PetDeathWorkflow workflow;

        private Fixture(boolean keepSkills) {
            this.keepSkills = keepSkills;
            workflow = newWorkflow();
        }

        private PetDeathWorkflow newWorkflow() {
            return new PetDeathWorkflow(
                    pets,
                    work,
                    (ownerId, petId) -> {
                        pets.pet = pets.pet.withStatus(PetStatus.DEAD).withActive(false);
                        return CompletableFuture.completedFuture(new DeathResult.Died(pets.pet));
                    },
                    (ownerId, petId) -> {
                        resetCalls.incrementAndGet();
                        int refund = assignedPoints.getAndSet(0);
                        balance.addAndGet(refund);
                        return CompletableFuture.completedFuture(
                                new ResetResult.Reset(new TreeProgress(petId, Map.of()), refund));
                    },
                    (petId, ownerId) -> CompletableFuture.completedFuture(issuedTokens.computeIfAbsent(
                            petId, ignored -> new SoulToken(TOKEN_ID, petId, ownerId, NOW))),
                    token -> {
                        if (failFirstDelivery.compareAndSet(true, false)) {
                            return CompletableFuture.failedFuture(new IllegalStateException("delivery unavailable"));
                        }
                        deliveredTokens.add(token.tokenId());
                        return CompletableFuture.completedFuture(null);
                    },
                    () -> keepSkills,
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private static final class FakePets implements PetRepository {
        private OwnedPet pet;

        private FakePets(OwnedPet pet) {
            this.pet = pet;
        }

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            return CompletableFuture.completedFuture(pet.id().equals(petId) ? Optional.of(pet) : Optional.empty());
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(pet.owner().equals(ownerId) ? List.of(pet) : List.of());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet changed) {
            pet = changed;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeDeathWork implements PetDeathWorkRepository {
        private final Map<PetId, PetDeathWork> entries = new HashMap<>();

        @Override
        public CompletableFuture<Void> save(PetDeathWork deathWork) {
            entries.put(deathWork.petId(), deathWork);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<PetDeathWork>> findByPet(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(entries.get(petId)));
        }

        @Override
        public CompletableFuture<List<PetDeathWork>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(entries.values().stream()
                    .filter(entry -> entry.ownerId().equals(ownerId)).toList());
        }
    }

    private static OwnedPet alivePet() {
        return new OwnedPet(PET_ID, OWNER, new VariantId("dog_brown"), Optional.empty(),
                PetStatus.ALIVE, true, NOW);
    }
}
