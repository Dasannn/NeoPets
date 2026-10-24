package dev.dasannn.neopets.storage.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.AttributeLevelProgress;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class PlayerDataCacheTest {
    private static final PlayerId PLAYER = player("10000000-0000-0000-0000-000000000001");
    private static final PlayerId OTHER_PLAYER = player("10000000-0000-0000-0000-000000000002");
    private static final PetId PET = petId("20000000-0000-0000-0000-000000000001");
    private static final PetId OTHER_PET = petId("20000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-10-23T12:00:00Z");

    private FakeRepositories delegates;
    private PlayerDataCache cache;
    private CachingPlayerRepository players;
    private CachingPetRepository pets;
    private CachingTreeProgressRepository trees;

    @BeforeEach
    void setUp() {
        delegates = new FakeRepositories();
        cache = new PlayerDataCache(delegates, delegates, delegates);
        players = new CachingPlayerRepository(delegates, cache);
        pets = new CachingPetRepository(delegates, cache);
        trees = new CachingTreeProgressRepository(delegates, cache);
    }

    @Test
    void successfulReviveRefreshesTheLoadedPet() throws Exception {
        var dead = pet(PET, PLAYER, "dog_brown").withStatus(PetStatus.DEAD);
        var alive = dead.withStatus(PetStatus.ALIVE);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 7, List.of()));
        delegates.pets.put(PET, dead);
        await(cache.load(PLAYER));
        assertTrue(await(cache.revivePet(alive, () -> CompletableFuture.completedFuture(true))));
        assertEquals(alive, await(pets.findById(PET)).orElseThrow());
        assertEquals(List.of(alive), await(pets.findByOwner(PLAYER)));
    }

    @Test
    void reviveUpdatesCacheOnlyAfterDurabilityAndOrdersUnloadAfterIt() throws Exception {
        var dead = pet(PET, PLAYER, "dog_brown").withStatus(PetStatus.DEAD);
        var alive = dead.withStatus(PetStatus.ALIVE);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 7, List.of()));
        delegates.pets.put(PET, dead);
        await(cache.load(PLAYER));
        assertFalse(await(cache.revivePet(alive, () -> CompletableFuture.completedFuture(false))));
        assertThrows(ExecutionException.class, () -> await(cache.revivePet(alive,
                () -> CompletableFuture.failedFuture(new IllegalStateException("write failed")))));
        assertEquals(dead, await(pets.findById(PET)).orElseThrow());
        var durable = new CompletableFuture<Boolean>();
        var revive = cache.revivePet(alive, () -> durable.thenApply(success -> {
            delegates.pets.put(PET, alive);
            return success;
        }));
        assertEquals(dead, await(pets.findById(PET)).orElseThrow());
        var unload = cache.unload(PLAYER);
        assertFalse(unload.isDone());
        durable.complete(true);
        assertTrue(await(revive));
        await(unload);
        assertEquals(alive, delegates.pets.get(PET));
        await(cache.load(PLAYER));
        assertEquals(alive, await(pets.findById(PET)).orElseThrow());
    }

    @Test
    void deathResetWaitsForDurabilityBeforeQueuedUnload() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 7, List.of()));
        delegates.pets.put(PET, pet(PET, PLAYER, "dog_brown"));
        delegates.progress.put(PET, progress(PET, 2, 2));
        await(cache.load(PLAYER));
        var zeroed = progress(PET, 0, 0);
        var durable = new CompletableFuture<Integer>();
        var reset = cache.resetForDeath(PLAYER, zeroed, () -> durable.thenApply(balance -> {
            delegates.players.put(PLAYER, new PlayerProfile(PLAYER, balance, List.of()));
            delegates.progress.put(PET, zeroed);
            return balance;
        }));
        var unload = cache.unload(PLAYER);
        assertFalse(unload.isDone());
        assertEquals(7, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());

        durable.complete(9);
        assertEquals(9, await(reset));
        await(unload);

        assertEquals(9, delegates.players.get(PLAYER).skillPointBalance());
        assertEquals(zeroed, delegates.progress.get(PET));
    }

    @Test
    void deathResetRefreshesCachedBalanceAndProgressOnlyAfterSuccess() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 7, List.of()));
        delegates.pets.put(PET, pet(PET, PLAYER, "dog_brown"));
        var before = progress(PET, 2, 2);
        delegates.progress.put(PET, before);
        await(cache.load(PLAYER));
        var zeroed = progress(PET, 0, 0);
        assertThrows(ExecutionException.class, () -> await(cache.resetForDeath(PLAYER, zeroed,
                () -> CompletableFuture.failedFuture(new IllegalStateException("refund failed")))));
        assertEquals(7, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
        assertEquals(Optional.of(before), await(trees.load(PET)));

        await(cache.resetForDeath(PLAYER, zeroed, () -> CompletableFuture.completedFuture(9)));
        assertEquals(9, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
        assertEquals(Optional.of(zeroed), await(trees.load(PET)));
    }

    @Test
    void missesUseDelegatesAndLoadedReadsUseTheCompleteMemorySnapshot() throws Exception {
        var ownedPet = pet(PET, PLAYER, "dog_brown");
        var progress = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 7, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, progress);

        assertEquals(Optional.empty(), cache.loadedProfile(PLAYER));
        assertEquals(7, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
        assertEquals(List.of(ownedPet), await(pets.findByOwner(PLAYER)));
        assertEquals(Optional.of(progress), await(trees.load(PET)));
        assertEquals(1, delegates.playerReads.get());
        assertEquals(1, delegates.petOwnerReads.get());
        assertEquals(1, delegates.treeReads.get());

        var loaded = await(cache.load(PLAYER));
        assertEquals(new PlayerProfile(PLAYER, 7, List.of(ownedPet)), loaded);
        assertEquals(Optional.of(loaded), cache.loadedProfile(PLAYER));
        int playerReadsAfterLoad = delegates.playerReads.get();
        int ownerReadsAfterLoad = delegates.petOwnerReads.get();
        int petReadsAfterLoad = delegates.petReads.get();
        int treeReadsAfterLoad = delegates.treeReads.get();

        assertEquals(Optional.of(loaded), await(players.findById(PLAYER)));
        assertEquals(List.of(ownedPet), await(pets.findByOwner(PLAYER)));
        assertEquals(Optional.of(ownedPet), await(pets.findById(PET)));
        assertEquals(Optional.of(progress), await(trees.load(PET)));
        assertEquals(playerReadsAfterLoad, delegates.playerReads.get());
        assertEquals(ownerReadsAfterLoad, delegates.petOwnerReads.get());
        assertEquals(petReadsAfterLoad, delegates.petReads.get());
        assertEquals(treeReadsAfterLoad, delegates.treeReads.get());
    }

    @Test
    void loadCachesMissingProfileAndMissingTreeProgress() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        delegates.pets.put(PET, ownedPet);

        assertEquals(new PlayerProfile(PLAYER, 0, List.of(ownedPet)), await(cache.load(PLAYER)));
        int playerReads = delegates.playerReads.get();
        int treeReads = delegates.treeReads.get();

        assertEquals(Optional.of(new PlayerProfile(PLAYER, 0, List.of(ownedPet))), await(players.findById(PLAYER)));
        assertEquals(Optional.empty(), await(trees.load(PET)));
        assertEquals(playerReads, delegates.playerReads.get());
        assertEquals(treeReads, delegates.treeReads.get());
    }

    @Test
    void writeThroughUpdatesMemoryBeforeDurabilityAndCompletesAfterTheDelegate() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 3, List.of()));
        await(cache.load(PLAYER));
        var durable = new CompletableFuture<Void>();
        delegates.playerSave = profile -> durable.thenRun(() -> delegates.players.put(profile.playerId(), profile));

        var save = players.save(new PlayerProfile(PLAYER, 9, List.of()));

        assertEquals(9, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
        assertFalse(save.isDone());
        assertEquals(3, delegates.players.get(PLAYER).skillPointBalance());

        durable.complete(null);
        await(save);
        assertEquals(9, delegates.players.get(PLAYER).skillPointBalance());
    }

    @Test
    void failedSkillPointWriteRollsBackMemory() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 4, List.of()));
        await(cache.load(PLAYER));
        var durable = new CompletableFuture<Void>();
        delegates.playerSave = ignored -> durable;

        var save = players.save(new PlayerProfile(PLAYER, 11, List.of()));
        assertEquals(11, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());

        durable.completeExceptionally(new IllegalStateException("database unavailable"));
        assertThrows(ExecutionException.class, () -> await(save));
        assertEquals(4, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
    }

    @Test
    void failedPetGrantAndDeleteEachRestoreThePreviousMemoryValue() throws Exception {
        var original = pet(PET, PLAYER, "dog_brown");
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, original);
        await(cache.load(PLAYER));

        delegates.petSave = ignored -> CompletableFuture.failedFuture(new IllegalStateException("save failed"));
        var grant = pet(OTHER_PET, PLAYER, "cat_black");
        assertThrows(ExecutionException.class, () -> await(pets.save(grant)));
        assertEquals(List.of(original), await(pets.findByOwner(PLAYER)));
        assertEquals(Optional.empty(), await(pets.findById(OTHER_PET)));

        delegates.petDelete = ignored -> CompletableFuture.failedFuture(new IllegalStateException("delete failed"));
        assertThrows(ExecutionException.class, () -> await(pets.deleteById(PET)));
        assertEquals(Optional.of(original), await(pets.findById(PET)));
        assertEquals(List.of(original), await(pets.findByOwner(PLAYER)));
    }

    @Test
    void failedTreeWriteRestoresThePreviousProgress() throws Exception {
        var ownedPet = pet(PET, PLAYER, "dog_brown");
        var original = progress(PET, 1, 0);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, original);
        await(cache.load(PLAYER));
        var durable = new CompletableFuture<Void>();
        delegates.treeSave = ignored -> durable;

        var changed = progress(PET, 2, 1);
        var save = trees.save(changed);
        assertEquals(Optional.of(changed), await(trees.load(PET)));

        durable.completeExceptionally(new IllegalStateException("tree save failed"));
        assertThrows(ExecutionException.class, () -> await(save));
        assertEquals(Optional.of(original), await(trees.load(PET)));
    }

    @Test
    void unloadOnlyEvictsWithoutRepeatingDurableWrites() throws Exception {
        var ownedPet = pet(PET, PLAYER, "parrot_red");
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 5, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, progress(PET, 1, 1));
        await(cache.load(PLAYER));
        delegates.resetWriteCounts();

        await(cache.unload(PLAYER));

        assertFalse(cache.isLoaded(PLAYER));
        assertEquals(0, cache.loadedPlayerCount());
        assertEquals(0, delegates.playerWrites.get());
        assertEquals(0, delegates.petWrites.get());
        assertEquals(0, delegates.treeWrites.get());

        int reads = delegates.playerReads.get();
        await(players.findById(PLAYER));
        assertEquals(reads + 1, delegates.playerReads.get());
    }

    @Test
    void delayedCleanupCannotEvictAProfileAcquiredByANewerSession() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 5, List.of()));
        await(cache.load(PLAYER, 101L));

        await(cache.load(PLAYER, 202L));
        await(cache.unload(PLAYER, 101L));

        assertTrue(cache.isLoaded(PLAYER));
        assertEquals(5, await(players.findById(PLAYER)).orElseThrow().skillPointBalance());

        await(cache.unload(PLAYER, 202L));
        assertFalse(cache.isLoaded(PLAYER));
    }

    @Test
    void rejectedDuplicateReleasesOnlyItsOwnCacheSession() throws Exception {
        await(cache.load(PLAYER, 301L));
        await(cache.load(PLAYER, 302L));

        await(cache.unload(PLAYER, 302L));

        assertTrue(cache.isLoaded(PLAYER));
        await(cache.unload(PLAYER, 301L));
        assertFalse(cache.isLoaded(PLAYER));
    }

    @Test
    void flushAllDoesNotIssueDuplicateWritesForCompletedOperations() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 1, List.of()));
        delegates.players.put(OTHER_PLAYER, new PlayerProfile(OTHER_PLAYER, 2, List.of()));
        await(cache.load(PLAYER));
        await(cache.load(OTHER_PLAYER));
        delegates.resetWriteCounts();

        await(cache.flushAll());

        assertEquals(0, delegates.playerWrites.get());
        assertEquals(0, delegates.petWrites.get());
        assertEquals(0, delegates.treeWrites.get());
        assertEquals(2, cache.loadedPlayerCount());
    }

    @Test
    void flushAllWaitsForEveryPlayerSequence() throws Exception {
        var ownedPet = pet(PET, PLAYER, "dog_brown");
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 1, List.of()));
        delegates.players.put(OTHER_PLAYER, new PlayerProfile(OTHER_PLAYER, 2, List.of()));
        delegates.pets.put(PET, ownedPet);
        await(cache.load(PLAYER));
        await(cache.load(OTHER_PLAYER));
        var playerGate = new CompletableFuture<Void>();
        var petGate = new CompletableFuture<Void>();
        delegates.playerSave = profile -> playerGate.thenRun(() -> delegates.players.put(profile.playerId(), profile));
        delegates.petSave = pet -> petGate.thenRun(() -> delegates.pets.put(pet.id(), pet));

        var playerWrite = players.save(new PlayerProfile(OTHER_PLAYER, 8, List.of()));
        var petWrite = pets.save(ownedPet);
        var flush = cache.flushAll();

        assertFalse(flush.isDone());
        playerGate.complete(null);
        await(playerWrite);
        assertFalse(flush.isDone());

        petGate.complete(null);
        await(petWrite);
        await(flush);
        assertEquals(2, cache.loadedPlayerCount());
    }

    @Test
    void flushAllWaitsForTreeOwnerLookupAndDurableWrite() throws Exception {
        var ownedPet = pet(PET, PLAYER, "dog_brown");
        var changedProgress = progress(PET, 2, 1);
        var ownerLookup = new CompletableFuture<Optional<OwnedPet>>();
        var treeWrite = new CompletableFuture<Void>();
        delegates.petFind = ignored -> ownerLookup;
        delegates.treeSave = ignored -> treeWrite;

        var save = trees.save(changedProgress);
        var flush = cache.flushAll();

        assertFalse(save.isDone());
        assertFalse(flush.isDone());
        assertEquals(0, delegates.treeWrites.get());

        ownerLookup.complete(Optional.of(ownedPet));
        assertEquals(1, delegates.treeWrites.get());
        assertFalse(save.isDone());
        assertFalse(flush.isDone());

        treeWrite.complete(null);
        await(save);
        await(flush);
    }

    @Test
    void flushAllWaitsForOwnerlessDeleteLookupAndFallbackWrite() throws Exception {
        var ownerLookup = new CompletableFuture<Optional<OwnedPet>>();
        var deleteWrite = new CompletableFuture<Void>();
        delegates.petFind = ignored -> ownerLookup;
        delegates.petDelete = ignored -> deleteWrite;

        var delete = pets.deleteById(PET);
        var flush = cache.flushAll();

        assertFalse(delete.isDone());
        assertFalse(flush.isDone());
        assertEquals(0, delegates.petDeletes.get());

        ownerLookup.complete(Optional.empty());
        assertEquals(1, delegates.petDeletes.get());
        assertFalse(delete.isDone());
        assertFalse(flush.isDone());

        deleteWrite.complete(null);
        await(delete);
        await(flush);
    }

    @Test
    void cancellingOwnerlessDeleteDoesNotLetFlushPassItsPendingWrite() throws Exception {
        var ownerLookup = new CompletableFuture<Optional<OwnedPet>>();
        var deleteWrite = new CompletableFuture<Void>();
        delegates.petFind = ignored -> ownerLookup;
        delegates.petDelete = ignored -> deleteWrite;

        var delete = pets.deleteById(PET);
        ownerLookup.complete(Optional.empty());
        assertEquals(1, delegates.petDeletes.get());
        assertTrue(delete.cancel(true));

        var flush = cache.flushAll();
        assertFalse(flush.isDone());

        deleteWrite.complete(null);
        await(flush);
        assertTrue(delete.isCancelled());
    }

    @Test
    void flushAllReportsFailureFromPendingOwnerlessDelete() throws Exception {
        var ownerLookup = new CompletableFuture<Optional<OwnedPet>>();
        var deleteWrite = new CompletableFuture<Void>();
        delegates.petFind = ignored -> ownerLookup;
        delegates.petDelete = ignored -> deleteWrite;

        pets.deleteById(PET);
        var flush = cache.flushAll();
        ownerLookup.complete(Optional.empty());
        var databaseFailure = new IllegalStateException("delete failed");
        deleteWrite.completeExceptionally(databaseFailure);

        var failure = assertThrows(ExecutionException.class, () -> await(flush));
        assertEquals(databaseFailure, failure.getCause());
    }

    @Test
    void unloadWaitsForQueuedPetOperationsBeforeEvicting() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        var changedProgress = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        await(cache.load(PLAYER));
        var petGate = new CompletableFuture<Void>();
        var treeGate = new CompletableFuture<Void>();
        delegates.petSave = pet -> petGate.thenRun(() -> delegates.pets.put(pet.id(), pet));
        delegates.treeSave = progress -> treeGate.thenRun(() -> delegates.progress.put(progress.petId(), progress));

        var petWrite = pets.save(ownedPet);
        var queuedTreeWrite = trees.save(changedProgress);
        var unload = cache.unload(PLAYER);

        assertFalse(unload.isDone());
        assertTrue(cache.isLoaded(PLAYER));
        assertEquals(0, delegates.treeWrites.get());

        petGate.complete(null);
        await(petWrite);
        assertEquals(1, delegates.treeWrites.get());
        assertFalse(unload.isDone());
        assertTrue(cache.isLoaded(PLAYER));

        treeGate.complete(null);
        await(queuedTreeWrite);
        await(unload);
        assertFalse(cache.isLoaded(PLAYER));
        assertEquals(changedProgress, delegates.progress.get(PET));
    }

    @Test
    void unloadWaitsForTreeWriteWhoseOwnerResolvesDuringLoad() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        var originalProgress = progress(PET, 1, 0);
        var changedProgress = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, originalProgress);
        var treeLoad = new CompletableFuture<Optional<TreeProgress>>();
        var ownerLookup = new CompletableFuture<Optional<OwnedPet>>();
        var treeWrite = new CompletableFuture<Void>();
        delegates.treeLoad = ignored -> treeLoad;
        delegates.petFind = ignored -> ownerLookup;
        delegates.treeSave = ignored -> treeWrite;

        var load = cache.load(PLAYER);
        var save = trees.save(changedProgress);
        var unload = cache.unload(PLAYER);

        treeLoad.complete(Optional.of(originalProgress));
        await(load);
        assertTrue(cache.isLoaded(PLAYER));
        assertFalse(unload.isDone());
        assertEquals(0, delegates.treeWrites.get());

        ownerLookup.complete(Optional.of(ownedPet));
        assertEquals(1, delegates.treeWrites.get());
        assertFalse(unload.isDone());
        assertTrue(cache.isLoaded(PLAYER));

        treeWrite.complete(null);
        await(save);
        await(unload);
        assertFalse(cache.isLoaded(PLAYER));
    }

    @Test
    void unloadWaitsForEveryQueuedTreeWriteOfTheSamePet() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        var firstProgress = progress(PET, 1, 0);
        var secondProgress = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        await(cache.load(PLAYER));
        var firstGate = new CompletableFuture<Void>();
        var secondGate = new CompletableFuture<Void>();
        var gates = new ArrayDeque<>(List.of(firstGate, secondGate));
        delegates.treeSave = progress -> gates.poll().thenRun(() -> delegates.progress.put(progress.petId(), progress));

        var firstWrite = trees.save(firstProgress);
        var secondWrite = trees.save(secondProgress);
        var unload = cache.unload(PLAYER);

        firstGate.complete(null);
        await(firstWrite);
        assertFalse(unload.isDone());
        assertTrue(cache.isLoaded(PLAYER));

        secondGate.complete(null);
        await(secondWrite);
        await(unload);
        assertFalse(cache.isLoaded(PLAYER));
        assertEquals(secondProgress, delegates.progress.get(PET));
    }

    @Test
    void reconnectDuringUnloadIsLoadedAfterTheOldSessionIsEvicted() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        var changedProgress = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        await(cache.load(PLAYER));
        int playerReadsBeforeReconnect = delegates.playerReads.get();
        var petGate = new CompletableFuture<Void>();
        var treeGate = new CompletableFuture<Void>();
        delegates.petSave = pet -> petGate.thenRun(() -> delegates.pets.put(pet.id(), pet));
        delegates.treeSave = progress -> treeGate.thenRun(() -> delegates.progress.put(progress.petId(), progress));

        var petWrite = pets.save(ownedPet);
        var treeWrite = trees.save(changedProgress);
        var unload = cache.unload(PLAYER);
        var reconnect = cache.load(PLAYER);

        assertFalse(unload.isDone());
        assertFalse(reconnect.isDone());
        assertEquals(0, delegates.treeWrites.get());

        petGate.complete(null);
        await(petWrite);
        assertEquals(1, delegates.treeWrites.get());
        assertFalse(unload.isDone());
        assertFalse(reconnect.isDone());

        treeGate.complete(null);
        await(treeWrite);
        await(unload);
        var reloaded = await(reconnect);

        assertTrue(cache.isLoaded(PLAYER));
        assertEquals(playerReadsBeforeReconnect + 1, delegates.playerReads.get());
        assertEquals(List.of(ownedPet), reloaded.ownedPets());
        assertEquals(Optional.of(changedProgress), await(trees.load(PET)));
    }

    @Test
    void treeWriteWaitsForPendingLoadAndCannotBeReplacedByItsStaleSnapshot() throws Exception {
        var ownedPet = pet(PET, PLAYER, "dog_brown");
        var original = progress(PET, 1, 0);
        var changed = progress(PET, 2, 1);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, original);
        var treeLoadGate = new CompletableFuture<Optional<TreeProgress>>();
        delegates.treeLoad = ignored -> treeLoadGate;

        var load = cache.load(PLAYER);
        var save = trees.save(changed);

        assertFalse(load.isDone());
        assertFalse(save.isDone());
        assertEquals(0, delegates.treeWrites.get());

        treeLoadGate.complete(Optional.of(original));
        await(load);
        await(save);

        assertEquals(Optional.of(changed), await(trees.load(PET)));
        assertEquals(changed, delegates.progress.get(PET));
        assertEquals(1, delegates.treeWrites.get());
        await(cache.unload(PLAYER));
        assertEquals(1, delegates.treeWrites.get());
    }

    @Test
    void petGrantQueuedDuringLoginLoadIsAddedToTheInstalledSnapshot() throws Exception {
        var grantedPet = pet(PET, PLAYER, "dog_brown");
        var petsAtLoadStart = new CompletableFuture<List<OwnedPet>>();
        delegates.petFindByOwner = ignored -> petsAtLoadStart;

        var load = cache.load(PLAYER);
        var grant = pets.save(grantedPet);

        assertFalse(load.isDone());
        assertFalse(grant.isDone());
        assertEquals(0, delegates.petWrites.get());

        petsAtLoadStart.complete(List.of());
        await(load);
        await(grant);

        assertEquals(List.of(grantedPet), cache.loadedPets(PLAYER).orElseThrow());
        assertEquals(grantedPet, delegates.pets.get(PET));
        assertEquals(1, delegates.petWrites.get());
    }

    @Test
    void deleteWaitsForPendingLoadAndLeavesNoStaleCachedPet() throws Exception {
        var ownedPet = pet(PET, PLAYER, "cat_black");
        var original = progress(PET, 1, 0);
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, ownedPet);
        delegates.progress.put(PET, original);
        var treeLoadGate = new CompletableFuture<Optional<TreeProgress>>();
        delegates.treeLoad = ignored -> treeLoadGate;

        var load = cache.load(PLAYER);
        var delete = pets.deleteById(PET);

        assertFalse(load.isDone());
        assertFalse(delete.isDone());
        assertEquals(0, delegates.petDeletes.get());

        treeLoadGate.complete(Optional.of(original));
        await(load);
        await(delete);

        assertEquals(Optional.empty(), await(pets.findById(PET)));
        assertEquals(List.of(), await(pets.findByOwner(PLAYER)));
        assertFalse(delegates.pets.containsKey(PET));
        assertEquals(1, delegates.petDeletes.get());
    }

    @Test
    void replacingAPetWithADifferentOwnerIsRejectedAndUnloadCannotRestoreIt() throws Exception {
        var original = pet(PET, PLAYER, "parrot_red");
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, original);
        await(cache.load(PLAYER));
        delegates.resetWriteCounts();

        var replacement = pet(PET, OTHER_PLAYER, "parrot_red");
        var failure = assertThrows(ExecutionException.class, () -> await(pets.save(replacement)));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(Optional.of(original), await(pets.findById(PET)));
        assertEquals(original, delegates.pets.get(PET));
        assertEquals(0, delegates.petWrites.get());

        await(cache.unload(PLAYER));
        assertEquals(original, delegates.pets.get(PET));
        assertEquals(0, delegates.petWrites.get());
    }

    @Test
    void duplicateVariantForLoadedPlayerUsesRepositoryConflictType() throws Exception {
        var original = pet(PET, PLAYER, "dog_brown");
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 0, List.of()));
        delegates.pets.put(PET, original);
        await(cache.load(PLAYER));
        delegates.resetWriteCounts();

        var duplicate = pet(OTHER_PET, PLAYER, "dog_brown");
        var failure = assertThrows(ExecutionException.class, () -> await(pets.save(duplicate)));

        var conflict = assertInstanceOf(DuplicatePetVariantException.class, failure.getCause());
        assertEquals(PLAYER, conflict.ownerId());
        assertEquals(original.variant(), conflict.variantId());
        assertEquals(List.of(original), await(pets.findByOwner(PLAYER)));
        assertEquals(0, delegates.petWrites.get());
    }

    @Test
    void concurrentWritesAreOrderedPerPlayerWithoutBlockingOtherPlayers() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 1, List.of()));
        delegates.players.put(OTHER_PLAYER, new PlayerProfile(OTHER_PLAYER, 1, List.of()));
        await(cache.load(PLAYER));
        await(cache.load(OTHER_PLAYER));
        var writes = new ConcurrentHashMap<PlayerId, List<CompletableFuture<Void>>>();
        delegates.playerSave = profile -> {
            var gate = new CompletableFuture<Void>();
            writes.computeIfAbsent(profile.playerId(), ignored -> new ArrayList<>()).add(gate);
            return gate.thenRun(() -> delegates.players.put(profile.playerId(), profile));
        };

        var first = players.save(new PlayerProfile(PLAYER, 2, List.of()));
        var second = players.save(new PlayerProfile(PLAYER, 3, List.of()));
        var other = players.save(new PlayerProfile(OTHER_PLAYER, 8, List.of()));

        assertEquals(1, writes.get(PLAYER).size());
        assertEquals(1, writes.get(OTHER_PLAYER).size());
        assertFalse(second.isDone());
        writes.get(OTHER_PLAYER).getFirst().complete(null);
        await(other);
        assertFalse(first.isDone());

        writes.get(PLAYER).getFirst().complete(null);
        await(first);
        assertEquals(2, writes.get(PLAYER).size());
        writes.get(PLAYER).get(1).complete(null);
        await(second);
        assertEquals(3, delegates.players.get(PLAYER).skillPointBalance());
        assertEquals(8, delegates.players.get(OTHER_PLAYER).skillPointBalance());
    }

    @Test
    void executorBackedConcurrentCallsRemainSequencedPerPlayer() throws Exception {
        delegates.players.put(PLAYER, new PlayerProfile(PLAYER, 1, List.of()));
        await(cache.load(PLAYER));
        int writeCount = 12;
        var firstWriteGate = new CompletableFuture<Void>();
        var delegateCalls = new AtomicInteger();
        delegates.playerSave = profile -> {
            int call = delegateCalls.incrementAndGet();
            if (call == 1) {
                return firstWriteGate.thenRun(() -> delegates.players.put(profile.playerId(), profile));
            }
            delegates.players.put(profile.playerId(), profile);
            return CompletableFuture.completedFuture(null);
        };

        try (var executor = Executors.newFixedThreadPool(writeCount)) {
            var ready = new CountDownLatch(writeCount);
            var start = new CountDownLatch(1);
            var submissions = new ArrayList<CompletableFuture<CompletableFuture<Void>>>();
            for (int index = 0; index < writeCount; index++) {
                int balance = index + 10;
                submissions.add(CompletableFuture.supplyAsync(() -> {
                    ready.countDown();
                    awaitLatch(start);
                    return players.save(new PlayerProfile(PLAYER, balance, List.of()));
                }, executor));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            await(CompletableFuture.allOf(submissions.toArray(CompletableFuture<?>[]::new)));
            assertEquals(1, delegateCalls.get());

            firstWriteGate.complete(null);
            var writes = submissions.stream().map(CompletableFuture::join).toArray(CompletableFuture<?>[]::new);
            await(CompletableFuture.allOf(writes));
            assertEquals(writeCount, delegateCalls.get());
            assertEquals(
                    delegates.players.get(PLAYER).skillPointBalance(),
                    await(players.findById(PLAYER)).orElseThrow().skillPointBalance());
        }
    }

    private static OwnedPet pet(PetId id, PlayerId owner, String variant) {
        return new OwnedPet(
                id,
                owner,
                new VariantId(variant),
                Optional.empty(),
                PetStatus.ALIVE,
                false,
                CREATED_AT);
    }

    private static TreeProgress progress(PetId petId, int unlocked, int active) {
        return new TreeProgress(petId, Map.of("health", new AttributeLevelProgress(unlocked, active, 3)));
    }

    private static PlayerId player(String value) {
        return new PlayerId(UUID.fromString(value));
    }

    private static PetId petId(String value) {
        return new PetId(UUID.fromString(value));
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while coordinating concurrent test", failure);
        }
    }

    private static final class FakeRepositories
            implements PlayerRepository, PetRepository, TreeProgressRepository {
        private final Map<PlayerId, PlayerProfile> players = new ConcurrentHashMap<>();
        private final Map<PetId, OwnedPet> pets = new ConcurrentHashMap<>();
        private final Map<PetId, TreeProgress> progress = new ConcurrentHashMap<>();
        private final AtomicInteger playerReads = new AtomicInteger();
        private final AtomicInteger petReads = new AtomicInteger();
        private final AtomicInteger petOwnerReads = new AtomicInteger();
        private final AtomicInteger treeReads = new AtomicInteger();
        private final AtomicInteger playerWrites = new AtomicInteger();
        private final AtomicInteger petWrites = new AtomicInteger();
        private final AtomicInteger petDeletes = new AtomicInteger();
        private final AtomicInteger treeWrites = new AtomicInteger();
        private Function<PlayerProfile, CompletableFuture<Void>> playerSave = this::storePlayer;
        private Function<PetId, CompletableFuture<Optional<OwnedPet>>> petFind = this::findPet;
        private Function<PlayerId, CompletableFuture<List<OwnedPet>>> petFindByOwner = this::findPetsByOwner;
        private Function<OwnedPet, CompletableFuture<Void>> petSave = this::storePet;
        private Function<PetId, CompletableFuture<Void>> petDelete = this::removePet;
        private Function<PetId, CompletableFuture<Optional<TreeProgress>>> treeLoad = this::loadProgress;
        private Function<TreeProgress, CompletableFuture<Void>> treeSave = this::storeProgress;

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
            playerReads.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.ofNullable(players.get(playerId)));
        }

        @Override
        public CompletableFuture<Void> save(PlayerProfile profile) {
            playerWrites.incrementAndGet();
            return playerSave.apply(profile);
        }

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            petReads.incrementAndGet();
            return petFind.apply(petId);
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            petOwnerReads.incrementAndGet();
            return petFindByOwner.apply(ownerId);
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet pet) {
            petWrites.incrementAndGet();
            return petSave.apply(pet);
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            petDeletes.incrementAndGet();
            return petDelete.apply(petId);
        }

        @Override
        public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
            treeReads.incrementAndGet();
            return treeLoad.apply(petId);
        }

        @Override
        public CompletableFuture<Void> save(TreeProgress treeProgress) {
            treeWrites.incrementAndGet();
            return treeSave.apply(treeProgress);
        }

        private CompletableFuture<Void> storePlayer(PlayerProfile profile) {
            players.put(profile.playerId(), profile);
            return CompletableFuture.completedFuture(null);
        }

        private CompletableFuture<Optional<OwnedPet>> findPet(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(pets.get(petId)));
        }

        private CompletableFuture<List<OwnedPet>> findPetsByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(pets.values().stream()
                    .filter(pet -> pet.owner().equals(ownerId))
                    .toList());
        }

        private CompletableFuture<Void> storePet(OwnedPet pet) {
            pets.put(pet.id(), pet);
            return CompletableFuture.completedFuture(null);
        }

        private CompletableFuture<Void> removePet(PetId petId) {
            pets.remove(petId);
            progress.remove(petId);
            return CompletableFuture.completedFuture(null);
        }

        private CompletableFuture<Void> storeProgress(TreeProgress treeProgress) {
            progress.put(treeProgress.petId(), treeProgress);
            return CompletableFuture.completedFuture(null);
        }

        private CompletableFuture<Optional<TreeProgress>> loadProgress(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(progress.get(petId)));
        }

        private void resetWriteCounts() {
            playerWrites.set(0);
            petWrites.set(0);
            petDeletes.set(0);
            treeWrites.set(0);
        }
    }
}
