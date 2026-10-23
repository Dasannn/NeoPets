package dev.dasannn.neopets.core.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.event.DomainEvent;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.PetDismissed;
import dev.dasannn.neopets.core.event.PetDied;
import dev.dasannn.neopets.core.event.PetGiven;
import dev.dasannn.neopets.core.event.PetRevived;
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
import dev.dasannn.neopets.core.port.SoulTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
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
        events.subscribe(PetDied.class, publishedEvents::add);
        events.subscribe(PetRevived.class, publishedEvents::add);
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
    void drainsFiveThousandQueuedServiceOperationsWithoutExhaustingTheStack() {
        var player = player(80);
        var delayedPets = new CompletableFuture<List<OwnedPet>>();
        pets.nextFindByOwner = delayedPets;
        var futures = new ArrayList<CompletableFuture<GiveResult>>();

        for (int index = 0; index < 5_000; index++) {
            futures.add(service.giveSpecific(player, LIGHT.id()));
        }

        assertEquals(1, pets.findByOwnerCalls);
        delayedPets.complete(List.of());

        assertTrue(futures.stream().allMatch(CompletableFuture::isDone));
        assertInstanceOf(GiveResult.Granted.class, futures.getFirst().join());
        for (var future : futures.subList(1, futures.size())) {
            assertEquals(new GiveResult.AlreadyOwned(LIGHT.id()), future.join());
        }
        assertEquals(1, pets.saveCalls);
    }

    @Test
    void repositoryFailureDoesNotPreventTheNextQueuedOperation() {
        var player = player(801);
        var delayedPets = new CompletableFuture<List<OwnedPet>>();
        pets.nextFindByOwner = delayedPets;
        pets.nextSaveFailure = new IllegalStateException("storage unavailable");

        var failed = service.giveSpecific(player, LIGHT.id());
        var following = service.giveSpecific(player, HEAVY.id());
        delayedPets.complete(List.of());

        assertThrows(CompletionException.class, failed::join);
        var granted = assertInstanceOf(GiveResult.Granted.class, following.join());
        assertEquals(HEAVY.id(), granted.pet().variant());
    }

    @Test
    void concurrentGrantsFromSeparateServiceInstancesUseDifferentPetIds() {
        var player = player(802);
        var firstRepository = new FakePetRepository();
        var secondRepository = new FakePetRepository();
        var firstLookup = new CompletableFuture<List<OwnedPet>>();
        var secondLookup = new CompletableFuture<List<OwnedPet>>();
        firstRepository.nextFindByOwner = firstLookup;
        secondRepository.nextFindByOwner = secondLookup;
        var firstService = service(
                new InMemoryVariantCatalog(List.of(LIGHT)), () -> 0.0,
                firstRepository, new RecordingEntityGateway());
        var secondService = service(
                new InMemoryVariantCatalog(List.of(LIGHT)), () -> 0.0,
                secondRepository, new RecordingEntityGateway());

        var first = firstService.giveSpecific(player, LIGHT.id());
        var second = secondService.giveSpecific(player, LIGHT.id());
        firstLookup.complete(List.of());
        secondLookup.complete(List.of());

        var firstGrant = assertInstanceOf(GiveResult.Granted.class, first.join());
        var secondGrant = assertInstanceOf(GiveResult.Granted.class, second.join());
        assertNotEquals(firstGrant.pet().id(), secondGrant.pet().id());
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
    void summonComposesRepositoryAndGatewayWorkAcrossDifferentWorkerThreads() throws Exception {
        var player = player(1201);
        var inactive = pet(12010, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        var petLookup = new CompletableFuture<Optional<OwnedPet>>();
        var ownerLookup = new CompletableFuture<List<OwnedPet>>();
        var spawnCompletion = new CompletableFuture<Void>();
        pets.nextFindById = petLookup;
        pets.nextFindByOwner = ownerLookup;
        entities.nextSpawn = spawnCompletion;

        try (var repositoryWorker = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("repository-worker").factory());
             var gatewayWorker = Executors.newSingleThreadExecutor(
                     Thread.ofPlatform().name("gateway-worker").factory())) {
            var result = service.summon(player, inactive.id());

            repositoryWorker.submit(() -> {
                petLookup.complete(Optional.of(inactive));
                ownerLookup.complete(List.of(inactive));
            }).get();

            assertFalse(result.isDone());
            assertEquals("repository-worker", entities.lastSpawnCallThread);

            gatewayWorker.submit(() -> spawnCompletion.complete(null)).get();

            assertInstanceOf(SummonResult.Summoned.class, result.join());
            assertEquals("gateway-worker", pets.lastSaveCallThread);
        }
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
    void failedPersistenceAwaitsCompensationCompletedOnAnotherWorkerThread() throws Exception {
        var player = player(1231);
        var inactive = pet(12310, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        var saveCompletion = new CompletableFuture<Void>();
        var compensationCompletion = new CompletableFuture<Void>();
        pets.nextSave = saveCompletion;
        entities.nextDespawn = compensationCompletion;

        try (var repositoryWorker = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().name("repository-worker").factory());
             var gatewayWorker = Executors.newSingleThreadExecutor(
                     Thread.ofPlatform().name("gateway-worker").factory())) {
            var result = service.summon(player, inactive.id());

            repositoryWorker.submit(() -> saveCompletion.completeExceptionally(
                    new IllegalStateException("save failed"))).get();

            assertFalse(result.isDone());
            assertEquals("repository-worker", entities.lastDespawnCallThread);

            gatewayWorker.submit(() -> compensationCompletion.complete(null)).get();

            assertThrows(CompletionException.class, result::join);
            assertTrue(publishedEvents.isEmpty());
        }
    }

    @Test
    void dismissRejectsMissingDeadInTransitAndInactivePets() {
        var player = player(13);
        var missingId = petId(130);
        assertEquals(new DismissResult.NotOwned(missingId), service.dismiss(player, missingId).join());

        var foreign = pet(1301, player(1301), LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(foreign);
        assertEquals(new DismissResult.NotOwned(foreign.id()), service.dismiss(player, foreign.id()).join());

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

    @Test
    void renameRejectsPetsNotOwnedByThePlayer() {
        var owner = player(150);
        var stranger = player(151);
        var pet = pet(1500, owner, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(pet);

        assertEquals(new RenameResult.NotOwned(pet.id()), service.rename(stranger, pet.id(), "Luna").join());
        assertTrue(pets.stored.get(pet.id()).customName().isEmpty());
    }

    @Test
    void renameIsSerializedWithSummonWithoutLosingEitherChange() {
        var player = player(152);
        var inactive = pet(1520, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(inactive);
        var delayedRenameSave = new CompletableFuture<Void>();
        pets.nextSave = delayedRenameSave;

        var renamed = service.rename(player, inactive.id(), "Luna");
        var summoned = service.summon(player, inactive.id());

        assertFalse(renamed.isDone());
        assertFalse(summoned.isDone());
        assertEquals(1, pets.findByIdCalls);
        delayedRenameSave.complete(null);

        assertEquals("Luna", assertInstanceOf(RenameResult.Renamed.class, renamed.join())
                .pet().customName().orElseThrow());
        assertInstanceOf(SummonResult.Summoned.class, summoned.join());
        OwnedPet persisted = pets.stored.get(inactive.id());
        assertEquals("Luna", persisted.customName().orElseThrow());
        assertTrue(persisted.active());
        assertEquals(2, pets.findByIdCalls);
    }

    @Test
    void renameDefensivelyRejectsNamesThatCannotFitTheGuiContract() {
        var player = player(153);
        var pet = pet(1530, player, LIGHT.id(), PetStatus.ALIVE, false);
        pets.store(pet);

        assertThrows(IllegalArgumentException.class, () -> service.rename(player, pet.id(), " "));
        assertThrows(IllegalArgumentException.class, () -> service.rename(player, pet.id(), "a".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> service.rename(player, pet.id(), "line\nbreak"));
    }

    @Test
    void deathPersistsAnInactiveDeadPetOnceAndPublishesItsTransition() {
        var player = player(160);
        var active = pet(1600, player, LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(active);

        var result = assertInstanceOf(DeathResult.Died.class, service.die(player, active.id()).join());

        assertEquals(PetStatus.DEAD, result.pet().status());
        assertFalse(result.pet().active());
        assertEquals(result.pet(), pets.stored.get(active.id()));
        assertEquals(List.of(new PetDied(active.id(), player)), publishedEvents);
        assertInstanceOf(DeathResult.AlreadyDead.class, service.die(player, active.id()).join());
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void deathRejectsMissingForeignAndInTransitPets() {
        var player = player(161);
        var missing = petId(1610);
        assertEquals(new DeathResult.NotOwned(missing), service.die(player, missing).join());

        var foreign = pet(1611, player(999), LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(foreign);
        assertEquals(new DeathResult.NotOwned(foreign.id()), service.die(player, foreign.id()).join());

        var inTransit = pet(1612, player, LIGHT.id(), PetStatus.IN_TRANSIT, false);
        pets.clearAndStore(inTransit);
        assertEquals(new DeathResult.InTransit(inTransit), service.die(player, inTransit.id()).join());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void reviveOnlyTransitionsADeadOwnedPetAndLeavesItInactive() {
        var player = player(162);
        var dead = pet(1620, player, LIGHT.id(), PetStatus.DEAD, true);
        pets.store(dead);

        var result = assertInstanceOf(ReviveResult.Revived.class, service.revive(player, dead.id()).join());

        assertEquals(PetStatus.ALIVE, result.pet().status());
        assertFalse(result.pet().active());
        assertEquals(List.of(new PetRevived(dead.id(), player)), publishedEvents);
        assertInstanceOf(ReviveResult.AlreadyAlive.class, service.revive(player, dead.id()).join());
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void soulReviveConsumesOneValidTokenAndRejectsItsDuplicateCopy() {
        var player = player(164);
        var dead = pet(1640, player, LIGHT.id(), PetStatus.DEAD, false);
        var tokenId = new UUID(2, 1640);
        var soulTokens = new FakeSoulTokens();
        var souls = new SoulItemService(soulTokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);
        pets.store(dead);
        souls.issue(dead.id(), player).join();

        var revived = assertInstanceOf(ReviveResult.Revived.class,
                service.revive(player, dead.id(), tokenId, player, souls).join());

        assertEquals(PetStatus.ALIVE, revived.pet().status());
        assertEquals(new ReviveResult.InvalidSoul(dead.id()),
                service.revive(player, dead.id(), tokenId, player, souls).join());
        assertEquals(1, soulTokens.consumptions);
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void failedSoulRevivalCanBeRetriedWithTheSameToken() {
        var player = player(164);
        var dead = pet(1640, player, LIGHT.id(), PetStatus.DEAD, false);
        var tokens = new FakeSoulTokens();
        var souls = new SoulItemService(tokens, Clock.fixed(NOW, ZoneOffset.UTC));
        pets.store(dead);
        var token = souls.issue(dead.id(), player).join();
        pets.nextSaveFailure = new IllegalStateException("database unavailable");

        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.revive(player, dead.id(), token.tokenId(), player, souls).join());
        assertEquals(PetStatus.DEAD, pets.stored.get(dead.id()).status());
        assertTrue(souls.isUsable(token.tokenId(), dead.id(), player).join());
        assertTrue(publishedEvents.isEmpty());
        assertInstanceOf(ReviveResult.Revived.class,
                service.revive(player, dead.id(), token.tokenId(), player, souls).join());
        assertEquals(1, tokens.consumptions);
    }

    @Test
    void soulReviveRejectsAnotherOwnerWithoutConsumingTheToken() {
        var player = player(165);
        var otherOwner = player(166);
        var dead = pet(1650, otherOwner, LIGHT.id(), PetStatus.DEAD, false);
        var tokenId = new UUID(2, 1650);
        var soulTokens = new FakeSoulTokens();
        var souls = new SoulItemService(soulTokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);
        pets.store(dead);
        souls.issue(dead.id(), otherOwner).join();

        assertEquals(new ReviveResult.NotOwned(dead.id()),
                service.revive(player, dead.id(), tokenId, otherOwner, souls).join());
        assertEquals(0, soulTokens.consumptions);
        assertEquals(PetStatus.DEAD, pets.stored.get(dead.id()).status());
    }

    @Test
    void soulReviveRejectsAnAlivePetWithoutConsumingItsValidToken() {
        var player = player(167);
        var alive = pet(1670, player, LIGHT.id(), PetStatus.ALIVE, false);
        var tokenId = new UUID(2, 1670);
        var soulTokens = new FakeSoulTokens();
        var souls = new SoulItemService(soulTokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);
        pets.store(alive);
        souls.issue(alive.id(), player).join();

        assertEquals(new ReviveResult.AlreadyAlive(alive),
                service.revive(player, alive.id(), tokenId, player, souls).join());
        assertEquals(0, soulTokens.consumptions);
        assertTrue(souls.isUsable(tokenId, alive.id(), player).join());
    }

    @Test
    void concurrentDuplicateRevivesWaitForTheFirstPersistenceAndOnlyConsumeOnce() {
        var player = player(168);
        var dead = pet(1680, player, LIGHT.id(), PetStatus.DEAD, false);
        var tokenId = new UUID(2, 1680);
        var tokens = new FakeSoulTokens();
        var souls = new SoulItemService(tokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);
        pets.store(dead);
        souls.issue(dead.id(), player).join();
        var saved = new CompletableFuture<Void>();
        pets.nextSave = saved;

        var first = service.revive(player, dead.id(), tokenId, player, souls);
        var duplicate = service.revive(player, dead.id(), tokenId, player, souls);

        assertFalse(first.isDone());
        assertFalse(duplicate.isDone());
        assertEquals(0, tokens.consumptions);
        saved.complete(null);
        assertInstanceOf(ReviveResult.Revived.class, first.join());
        assertInstanceOf(ReviveResult.InvalidSoul.class, duplicate.join());
        assertEquals(1, tokens.consumptions);
        assertEquals(1, publishedEvents.size());
    }

    @Test
    void soulReviveRejectsMismatchedPetAndUnknownTokenWithoutChangingThePet() {
        var player = player(169);
        var dead = pet(1690, player, LIGHT.id(), PetStatus.DEAD, false);
        var tokenId = new UUID(2, 1690);
        var tokens = new FakeSoulTokens();
        var souls = new SoulItemService(tokens, Clock.fixed(NOW, ZoneOffset.UTC), () -> tokenId);
        pets.store(dead);
        souls.issue(dead.id(), player).join();

        assertInstanceOf(ReviveResult.InvalidSoul.class,
                service.revive(player, petId(1691), tokenId, player, souls).join());
        assertInstanceOf(ReviveResult.InvalidSoul.class,
                service.revive(player, dead.id(), UUID.randomUUID(), player, souls).join());
        assertEquals(0, tokens.consumptions);
        assertTrue(souls.isUsable(tokenId, dead.id(), player).join());
        assertEquals(PetStatus.DEAD, pets.stored.get(dead.id()).status());
        assertTrue(publishedEvents.isEmpty());
    }

    @Test
    void concurrentDeathAndSummonAreSequencedWithoutResurrectingThePet() {
        var player = player(163);
        var active = pet(1630, player, LIGHT.id(), PetStatus.ALIVE, true);
        pets.store(active);
        var delayedDeathSave = new CompletableFuture<Void>();
        pets.nextSave = delayedDeathSave;

        var death = service.die(player, active.id());
        var summon = service.summon(player, active.id());
        assertFalse(death.isDone());
        assertFalse(summon.isDone());

        delayedDeathSave.complete(null);

        assertInstanceOf(DeathResult.Died.class, death.join());
        assertInstanceOf(SummonResult.Dead.class, summon.join());
        assertEquals(PetStatus.DEAD, pets.stored.get(active.id()).status());
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
        private CompletableFuture<Optional<OwnedPet>> nextFindById;
        private CompletableFuture<List<OwnedPet>> nextFindByOwner;
        private CompletableFuture<Void> nextSave;
        private Throwable nextSaveFailure;
        private FakeSoulTokens tokens;
        private String lastSaveCallThread;
        private int findByIdCalls;
        private int findByOwnerCalls;
        private int saveCalls;

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            findByIdCalls++;
            if (nextFindById != null) {
                var result = nextFindById;
                nextFindById = null;
                return result;
            }
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
            lastSaveCallThread = Thread.currentThread().getName();
            if (nextSave != null) {
                var result = nextSave;
                nextSave = null;
                return result.thenRun(() -> stored.put(pet.id(), pet));
            }
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

        @Override
        public CompletableFuture<Boolean> reviveWithSoul(OwnedPet pet, UUID tokenId, Instant consumedAt) {
            return save(pet).thenCompose(ignored -> tokens.consume(tokenId, pet.id(), pet.owner(), consumedAt));
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
        private CompletableFuture<Void> nextSpawn;
        private CompletableFuture<Void> nextDespawn;
        private Throwable spawnFailure;
        private Throwable despawnFailure;
        private String lastSpawnCallThread;
        private String lastDespawnCallThread;

        @Override
        public CompletableFuture<Void> spawn(PetId petId) {
            lastSpawnCallThread = Thread.currentThread().getName();
            if (spawnFailure != null) {
                return CompletableFuture.failedFuture(spawnFailure);
            }
            spawned.add(petId);
            if (nextSpawn != null) {
                var result = nextSpawn;
                nextSpawn = null;
                return result;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> despawn(PetId petId) {
            lastDespawnCallThread = Thread.currentThread().getName();
            if (despawnFailure != null) {
                return CompletableFuture.failedFuture(despawnFailure);
            }
            despawned.add(petId);
            if (nextDespawn != null) {
                var result = nextDespawn;
                nextDespawn = null;
                return result;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> applyEffects(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private final class FakeSoulTokens implements SoulTokenRepository {
        FakeSoulTokens() {
            pets.tokens = this;
        }
        private final Map<UUID, Soul> tokens = new LinkedHashMap<>();
        private final Set<UUID> delivered = new HashSet<>();
        private int consumptions;

        @Override
        public CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) {
            tokens.put(tokenId, new Soul(petId, ownerId, false));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId) {
            Soul soul = tokens.get(tokenId);
            return CompletableFuture.completedFuture(soul != null && !soul.consumed()
                    && soul.petId().equals(petId) && soul.ownerId().equals(ownerId));
        }

        @Override
        public CompletableFuture<Boolean> consume(
                UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt
        ) {
            boolean usable = isUsable(tokenId, petId, ownerId).join();
            if (usable) {
                Soul soul = tokens.get(tokenId);
                tokens.put(tokenId, new Soul(soul.petId(), soul.ownerId(), true));
                consumptions++;
            }
            return CompletableFuture.completedFuture(usable);
        }

        @Override
        public CompletableFuture<Optional<SoulToken>> findUnconsumedByPet(PetId petId) {
            return CompletableFuture.completedFuture(tokens.entrySet().stream()
                    .filter(entry -> entry.getValue().petId().equals(petId) && !entry.getValue().consumed())
                    .map(entry -> new SoulToken(entry.getKey(), petId, entry.getValue().ownerId(), Instant.EPOCH))
                    .findFirst());
        }

        @Override
        public CompletableFuture<Boolean> wasDelivered(UUID tokenId, PetId petId, PlayerId ownerId) {
            return CompletableFuture.completedFuture(delivered.contains(tokenId));
        }

        @Override
        public CompletableFuture<Boolean> markDelivered(
                UUID tokenId, PetId petId, PlayerId ownerId, Instant deliveredAt
        ) {
            return CompletableFuture.completedFuture(tokens.containsKey(tokenId) && delivered.add(tokenId));
        }
    }

    private record Soul(PetId petId, PlayerId ownerId, boolean consumed) {
    }
}
