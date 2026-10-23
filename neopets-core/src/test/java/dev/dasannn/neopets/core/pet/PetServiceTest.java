package dev.dasannn.neopets.core.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.event.DomainEvent;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.PetDismissed;
import dev.dasannn.neopets.core.event.PetGiven;
import dev.dasannn.neopets.core.event.PetSummoned;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.model.VariantAppearance;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PetServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final PetTypeId DOG = new PetTypeId("dog");
    private static final PetTypeId CAT = new PetTypeId("cat");
    private static final PetVariant LIGHT = variant("dog_light", DOG, 1.0);
    private static final PetVariant HEAVY = variant("dog_heavy", DOG, 3.0);
    private static final PetVariant CAT_BLACK = variant("cat_black", CAT, 10.0);

    private final FakePetRepository pets = new FakePetRepository();
    private final RecordingEntityGateway entities = new RecordingEntityGateway();
    private final DomainEventBus events = new DomainEventBus();
    private final List<DomainEvent> publishedEvents = new ArrayList<>();
    private double randomValue;
    private int maxActivePets = 1;
    private PetService service;

    @BeforeEach
    void setUp() {
        events.subscribe(PetGiven.class, publishedEvents::add);
        events.subscribe(PetSummoned.class, publishedEvents::add);
        events.subscribe(PetDismissed.class, publishedEvents::add);
        service = service(new InMemoryVariantCatalog(List.of(LIGHT, HEAVY, CAT_BLACK)), () -> randomValue);
    }

    @Test
    void listGiveRejectsAnUnknownIdentifierWithoutPersistingAnything() {
        var player = player(1);
        var unknown = new VariantId("missing");

        var result = service.give(player, List.of(LIGHT.id(), unknown)).join();

        assertEquals(new GiveResult.UnknownVariant(unknown), result);
        assertTrue(pets.stored.isEmpty());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void listGiveSelectsByWeightOnlyAmongUnownedVariants() {
        var player = player(2);
        pets.store(pet(20, player, LIGHT.id(), PetStatus.ALIVE, false));
        randomValue = 0.99;

        var result = assertInstanceOf(GiveResult.Granted.class,
                service.give(player, List.of(LIGHT.id(), HEAVY.id())).join());

        assertEquals(HEAVY.id(), result.pet().variant());
    }

    @Test
    void listGiveSignalsAllOwnedAndNeverRepeats() {
        var player = player(3);
        pets.store(pet(30, player, LIGHT.id(), PetStatus.ALIVE, false));
        pets.store(pet(31, player, HEAVY.id(), PetStatus.ALIVE, false));

        var result = service.give(player, List.of(LIGHT.id(), HEAVY.id())).join();

        assertInstanceOf(GiveResult.AllOwned.class, result);
        assertEquals(2, pets.stored.size());
    }

    @Test
    void specificGiveReportsUnknownAndAlreadyOwnedAsBusinessResults() {
        var player = player(4);
        pets.store(pet(40, player, LIGHT.id(), PetStatus.ALIVE, false));

        assertEquals(new GiveResult.UnknownVariant(new VariantId("missing")),
                service.giveSpecific(player, new VariantId("missing")).join());
        assertEquals(new GiveResult.AlreadyOwned(LIGHT.id()),
                service.giveSpecific(player, LIGHT.id()).join());
    }

    @Test
    void grantUsesClockPersistsOnePetRowAndPublishesEvent() {
        var player = player(5);

        var result = assertInstanceOf(GiveResult.Granted.class,
                service.giveSpecific(player, LIGHT.id()).join());

        assertEquals(NOW, result.pet().createdAt());
        assertEquals(result.pet(), pets.stored.get(result.pet().id()));
        assertEquals(1, pets.saveCalls);
        assertEquals(List.of(new PetGiven(result.pet().id(), player, LIGHT.id())), publishedEvents);
    }

    @Test
    void duplicateIdsDoNotIncreaseAVariantsSelectionWeight() {
        var player = player(51);
        randomValue = 0.30;

        var result = assertInstanceOf(GiveResult.Granted.class,
                service.give(player, List.of(LIGHT.id(), LIGHT.id(), HEAVY.id())).join());

        assertEquals(HEAVY.id(), result.pet().variant());
    }

    @Test
    void failedPetSaveDoesNotPublishOrLeaveAPartialGrant() {
        var player = player(52);
        pets.nextSaveFailure = new IllegalStateException("storage unavailable");

        assertThrows(CompletionException.class, () -> service.giveSpecific(player, LIGHT.id()).join());

        assertTrue(pets.stored.isEmpty());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void uniquenessRaceMapsSpecificGrantToAlreadyOwned() {
        var player = player(53);
        pets.nextSaveFailure = new DuplicatePetVariantException(player, LIGHT.id());

        var result = service.giveSpecific(player, LIGHT.id()).join();

        assertEquals(new GiveResult.AlreadyOwned(LIGHT.id()), result);
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void uniquenessRaceRetriesAnotherRandomCandidate() {
        var player = player(54);
        randomValue = 0.0;
        pets.nextSaveFailure = new DuplicatePetVariantException(player, LIGHT.id());

        var result = assertInstanceOf(GiveResult.Granted.class,
                service.give(player, List.of(LIGHT.id(), HEAVY.id())).join());

        assertEquals(HEAVY.id(), result.pet().variant());
        assertEquals(2, pets.saveCalls);
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void randomGiveCanBeRestrictedToAType() {
        var player = player(6);
        randomValue = 0.99;

        var result = assertInstanceOf(GiveResult.Granted.class,
                service.giveRandom(player, Optional.of(DOG)).join());

        assertEquals(HEAVY.id(), result.pet().variant());
    }

    @Test
    void randomGiveSignalsAllOwnedWhenATypeHasNoCandidates() {
        var result = service.giveRandom(player(7), Optional.of(new PetTypeId("parrot"))).join();

        assertInstanceOf(GiveResult.AllOwned.class, result);
    }

    @Test
    void weightedSelectionFollowsConfiguredDistributionWithSeededRandomness() {
        var seededPets = new FakePetRepository();
        var seededService = service(
                new InMemoryVariantCatalog(List.of(LIGHT, HEAVY)),
                new Random(87123)::nextDouble,
                seededPets,
                new RecordingEntityGateway()
        );
        int heavySelections = 0;
        int samples = 4_000;

        for (int sample = 0; sample < samples; sample++) {
            var result = assertInstanceOf(GiveResult.Granted.class,
                    seededService.giveRandom(player(10_000 + sample), Optional.empty()).join());
            if (result.pet().variant().equals(HEAVY.id())) {
                heavySelections++;
            }
        }

        double heavyShare = (double) heavySelections / samples;
        assertTrue(heavyShare > 0.72 && heavyShare < 0.78, "heavy share was " + heavyShare);
    }

    @Test
    void concurrentGivesForOnePlayerStillNeverRepeat() {
        var player = player(8);
        var delayedPets = new CompletableFuture<List<OwnedPet>>();
        pets.nextFindByOwner = delayedPets;

        var first = service.giveSpecific(player, LIGHT.id());
        var second = service.giveSpecific(player, LIGHT.id());

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertEquals(1, pets.findByOwnerCalls);
        delayedPets.complete(List.of());
        assertInstanceOf(GiveResult.Granted.class, first.join());
        assertEquals(new GiveResult.AlreadyOwned(LIGHT.id()), second.join());
        assertEquals(1, pets.findByOwner(player).join().size());
    }

    @Test
    void synchronousEventListenerCanQueueAnotherOperationForTheSamePlayer() {
        var player = player(81);
        var reentrantResult = new AtomicReference<CompletableFuture<GiveResult>>();
        events.subscribe(PetGiven.class, event -> {
            if (event.variantId().equals(LIGHT.id())) {
                reentrantResult.set(service.giveSpecific(player, HEAVY.id()));
            }
        });

        var first = service.giveSpecific(player, LIGHT.id()).join();
        var second = reentrantResult.get().join();

        assertInstanceOf(GiveResult.Granted.class, first);
        assertEquals(HEAVY.id(), assertInstanceOf(GiveResult.Granted.class, second).pet().variant());
        assertEquals(2, pets.findByOwner(player).join().size());
    }

    @Test
    void summonRejectsPetsThatAreMissingForeignDeadInTransitOrAlreadyActive() {
        var owner = player(9);
        var stranger = player(10);
        var missingId = petId(90);
        assertEquals(new SummonResult.NotOwned(missingId), service.summon(owner, missingId).join());

        var foreign = pet(91, stranger, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(foreign);
        assertEquals(new SummonResult.NotOwned(foreign.id()), service.summon(owner, foreign.id()).join());

        var dead = pet(92, owner, LIGHT.id(), PetStatus.DEAD, false);
        pets.clearAndStore(dead);
        assertEquals(new SummonResult.Dead(dead), service.summon(owner, dead.id()).join());

        var inTransit = pet(93, owner, LIGHT.id(), PetStatus.IN_TRANSIT, false);
        pets.clearAndStore(inTransit);
        assertEquals(new SummonResult.InTransit(inTransit), service.summon(owner, inTransit.id()).join());

        var active = pet(94, owner, LIGHT.id(), PetStatus.ALIVE, true);
        pets.clearAndStore(active);
        assertEquals(new SummonResult.AlreadyActive(active), service.summon(owner, active.id()).join());
        assertTrue(entities.spawned.isEmpty());
    }

    @Test
    void summonRespectsRankLimitWithoutSpawning() {
        var player = player(11);
        var active = pet(110, player, LIGHT.id(), PetStatus.ALIVE, true);
        var inactive = pet(111, player, HEAVY.id(), PetStatus.ALIVE, false);
        pets.store(active);
        pets.store(inactive);

        var result = service.summon(player, inactive.id()).join();

        assertEquals(new SummonResult.LimitReached(1), result);
        assertFalse(pets.stored.get(inactive.id()).active());
        assertTrue(entities.spawned.isEmpty());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void summonPersistsSpawnsAndPublishesAfterValidation() {
        var player = player(12);
        var inactive = pet(120, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        maxActivePets = 2;

        var result = assertInstanceOf(SummonResult.Summoned.class,
                service.summon(player, inactive.id()).join());

        assertTrue(result.pet().active());
        assertTrue(pets.stored.get(inactive.id()).active());
        assertEquals(List.of(inactive.id()), entities.spawned);
        assertEquals(List.of(new PetSummoned(inactive.id(), player)), publishedEvents);
    }

    @Test
    void concurrentSummonsCannotExceedTheRankLimit() {
        var player = player(121);
        var firstPet = pet(1210, player, LIGHT.id(), PetStatus.ALIVE, false);
        var secondPet = pet(1211, player, HEAVY.id(), PetStatus.ALIVE, false);
        pets.store(firstPet);
        pets.store(secondPet);
        var delayedPets = new CompletableFuture<List<OwnedPet>>();
        pets.nextFindByOwner = delayedPets;

        var first = service.summon(player, firstPet.id());
        var second = service.summon(player, secondPet.id());

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        delayedPets.complete(List.of(firstPet, secondPet));
        assertInstanceOf(SummonResult.Summoned.class, first.join());
        assertEquals(new SummonResult.LimitReached(1), second.join());
        assertEquals(List.of(firstPet.id()), entities.spawned);
    }

    @Test
    void failedSpawnLeavesPetInactiveAndPublishesNoEvent() {
        var player = player(122);
        var inactive = pet(1220, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        entities.spawnFailure = new IllegalStateException("spawn failed");

        assertThrows(CompletionException.class, () -> service.summon(player, inactive.id()).join());

        assertFalse(pets.stored.get(inactive.id()).active());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void failedSummonPersistenceDespawnsTheEntityAgain() {
        var player = player(123);
        var inactive = pet(1230, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        pets.nextSaveFailure = new IllegalStateException("save failed");

        assertThrows(CompletionException.class, () -> service.summon(player, inactive.id()).join());

        assertFalse(pets.stored.get(inactive.id()).active());
        assertEquals(List.of(inactive.id()), entities.spawned);
        assertEquals(List.of(inactive.id()), entities.despawned);
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void dismissRejectsMissingDeadInTransitAndInactivePets() {
        var player = player(13);
        var missingId = petId(130);
        assertEquals(new DismissResult.NotOwned(missingId), service.dismiss(player, missingId).join());

        var dead = pet(131, player, LIGHT.id(), PetStatus.DEAD, true);
        pets.clearAndStore(dead);
        assertEquals(new DismissResult.Dead(dead), service.dismiss(player, dead.id()).join());

        var inTransit = pet(132, player, LIGHT.id(), PetStatus.IN_TRANSIT, true);
        pets.clearAndStore(inTransit);
        assertEquals(new DismissResult.InTransit(inTransit), service.dismiss(player, inTransit.id()).join());

        var inactive = pet(133, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.clearAndStore(inactive);
        assertEquals(new DismissResult.AlreadyInactive(inactive), service.dismiss(player, inactive.id()).join());
        assertTrue(entities.despawned.isEmpty());
    }

    @Test
    void dismissAlwaysSucceedsForAnAliveActiveOwnedPetRegardlessOfRankLimit() {
        var player = player(14);
        var active = pet(140, player, LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(active);
        maxActivePets = 0;

        var result = assertInstanceOf(DismissResult.Dismissed.class,
                service.dismiss(player, active.id()).join());

        assertFalse(result.pet().active());
        assertFalse(pets.stored.get(active.id()).active());
        assertEquals(List.of(active.id()), entities.despawned);
        assertEquals(List.of(new PetDismissed(active.id(), player)), publishedEvents);
    }

    @Test
    void failedDespawnLeavesPetActiveAndPublishesNoEvent() {
        var player = player(141);
        var active = pet(1410, player, LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(active);
        entities.despawnFailure = new IllegalStateException("despawn failed");

        assertThrows(CompletionException.class, () -> service.dismiss(player, active.id()).join());

        assertTrue(pets.stored.get(active.id()).active());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void failedDismissPersistenceRespawnsTheEntity() {
        var player = player(142);
        var active = pet(1420, player, LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(active);
        pets.nextSaveFailure = new IllegalStateException("save failed");

        assertThrows(CompletionException.class, () -> service.dismiss(player, active.id()).join());

        assertTrue(pets.stored.get(active.id()).active());
        assertEquals(List.of(active.id()), entities.despawned);
        assertEquals(List.of(active.id()), entities.spawned);
        assertTrue(publishedEvents.isEmpty());
    }

    private PetService service(VariantCatalog catalog, dev.dasannn.neopets.core.port.RandomSource random) {
        return service(catalog, random, pets, entities);
    }

    private PetService service(
            VariantCatalog catalog,
            dev.dasannn.neopets.core.port.RandomSource random,
            FakePetRepository petRepository,
            RecordingEntityGateway gateway
    ) {
        return new PetService(
                catalog,
                petRepository,
                gateway,
                ignored -> new RankLimits(maxActivePets, 3, 10),
                events,
                random,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static PetVariant variant(String id, PetTypeId type, double weight) {
        return new PetVariant(new VariantId(id), type, VariantAppearance.empty(), false, weight, "pet." + id);
    }

    private static PlayerId player(long value) {
        return new PlayerId(new UUID(0, value));
    }

    private static PetId petId(long value) {
        return new PetId(new UUID(1, value));
    }

    private static OwnedPet pet(
            long id,
            PlayerId owner,
            VariantId variant,
            PetStatus status,
            boolean active
    ) {
        return new OwnedPet(petId(id), owner, variant, Optional.empty(), status, active, NOW.minusSeconds(60));
    }

    private static final class FakePetRepository implements PetRepository {
        private final Map<PetId, OwnedPet> stored = new LinkedHashMap<>();
        private CompletableFuture<List<OwnedPet>> nextFindByOwner;
        private Throwable nextSaveFailure;
        private int findByOwnerCalls;
        private int saveCalls;

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(petId)));
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            findByOwnerCalls++;
            if (nextFindByOwner != null) {
                var result = nextFindByOwner;
                nextFindByOwner = null;
                return result;
            }
            return CompletableFuture.completedFuture(stored.values().stream()
                    .filter(pet -> pet.owner().equals(ownerId))
                    .toList());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet pet) {
            saveCalls++;
            if (nextSaveFailure != null) {
                var failure = nextSaveFailure;
                nextSaveFailure = null;
                return CompletableFuture.failedFuture(failure);
            }
            boolean duplicateVariant = stored.values().stream()
                    .anyMatch(existing -> !existing.id().equals(pet.id())
                            && existing.owner().equals(pet.owner())
                            && existing.variant().equals(pet.variant()));
            if (duplicateVariant) {
                return CompletableFuture.failedFuture(
                        new DuplicatePetVariantException(pet.owner(), pet.variant()));
            }
            stored.put(pet.id(), pet);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            stored.remove(petId);
            return CompletableFuture.completedFuture(null);
        }

        void store(OwnedPet pet) {
            stored.put(pet.id(), pet);
        }

        void clearAndStore(OwnedPet pet) {
            stored.clear();
            store(pet);
        }
    }

    private static final class RecordingEntityGateway implements PetEntityGateway {
        private final List<PetId> spawned = new ArrayList<>();
        private final List<PetId> despawned = new ArrayList<>();
        private Throwable spawnFailure;
        private Throwable despawnFailure;

        @Override
        public void spawn(PetId petId) {
            if (spawnFailure != null) {
                throw propagate(spawnFailure);
            }
            spawned.add(petId);
        }

        @Override
        public void despawn(PetId petId) {
            if (despawnFailure != null) {
                throw propagate(despawnFailure);
            }
            despawned.add(petId);
        }

        @Override
        public void applyEffects(PetId petId) {
        }

        private static RuntimeException propagate(Throwable failure) {
            if (failure instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            return new RuntimeException(failure);
        }
    }
}
