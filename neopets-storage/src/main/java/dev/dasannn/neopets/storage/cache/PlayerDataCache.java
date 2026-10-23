package dev.dasannn.neopets.storage.cache;

import dev.dasannn.neopets.core.concurrent.KeyedSequencer;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Keeps the complete persistence view of each online player in memory.
 *
 * <p>Call {@link #load(PlayerId, long)} before admitting a player and
 * {@link #unload(PlayerId, long)} after disconnect. The session-aware methods ensure delayed
 * cleanup from an earlier connection cannot evict a profile acquired by a later connection.
 * Mutations made through the caching repository decorators are serialized per player, applied
 * optimistically, and rolled back if their durable write fails. No cache monitor is held while a
 * repository future is pending.</p>
 */
public final class PlayerDataCache {
    private final Object monitor = new Object();
    private final Map<PlayerId, CachedPlayerData> players = new HashMap<>();
    private final Map<PetId, PlayerId> petOwners = new HashMap<>();
    private final KeyedSequencer<PlayerId> operations = new KeyedSequencer<>();
    private final Set<CompletableFuture<Void>> pendingOwnerOperations = new HashSet<>();
    private final PlayerRepository playerDelegate;
    private final PetRepository petDelegate;
    private final TreeProgressRepository treeDelegate;

    /** Creates a cache backed by the uncached persistence repositories. */
    public PlayerDataCache(
            PlayerRepository playerDelegate,
            PetRepository petDelegate,
            TreeProgressRepository treeDelegate
    ) {
        this.playerDelegate = Objects.requireNonNull(playerDelegate, "playerDelegate");
        this.petDelegate = Objects.requireNonNull(petDelegate, "petDelegate");
        this.treeDelegate = Objects.requireNonNull(treeDelegate, "treeDelegate");
    }

    /**
     * Asynchronously loads a player's balance, owned pets, and every pet's tree progress.
     * Concurrent calls for the same player share the per-player operation sequence.
     *
     * @return the fully assembled profile installed in the cache
     */
    public CompletableFuture<PlayerProfile> load(PlayerId playerId) {
        return load(playerId, null);
    }

    /**
     * Loads a player's complete profile and acquires it for one connection session.
     * Acquisition is serialized with conditional session unloads for the same player.
     *
     * @param playerId player to load
     * @param sessionToken unique token for this connection attempt
     * @return the fully assembled profile installed in the cache
     */
    public CompletableFuture<PlayerProfile> load(PlayerId playerId, long sessionToken) {
        return load(playerId, Long.valueOf(sessionToken));
    }

    private CompletableFuture<PlayerProfile> load(PlayerId playerId, Long sessionToken) {
        Objects.requireNonNull(playerId, "playerId");
        return operations.submit(playerId, () -> {
            synchronized (monitor) {
                var current = players.get(playerId);
                if (current != null) {
                    if (sessionToken != null) {
                        players.put(playerId, current.withSession(sessionToken));
                    }
                    return CompletableFuture.completedFuture(current.profile());
                }
            }
            return loadFromDelegates(playerId, sessionToken);
        });
    }

    /**
     * Evicts a player after all previously submitted writes for that player have completed.
     * An unloaded player is a successful no-op. The cache is strictly write-through, so eviction
     * never performs persistence writes.
     */
    public CompletableFuture<Void> unload(PlayerId playerId) {
        return unload(playerId, null);
    }

    /**
     * Releases one connection session and evicts only when it is the last owner of the snapshot.
     * A token that no longer owns the cached entry is a successful no-op.
     *
     * @param playerId player whose connection ended
     * @param sessionToken token returned for that connection attempt
     * @return completion after the conditional eviction has been serialized
     */
    public CompletableFuture<Void> unload(PlayerId playerId, long sessionToken) {
        return unload(playerId, Long.valueOf(sessionToken));
    }

    private CompletableFuture<Void> unload(PlayerId playerId, Long sessionToken) {
        Objects.requireNonNull(playerId, "playerId");
        List<CompletableFuture<Void>> unresolvedOperations;
        synchronized (monitor) {
            unresolvedOperations = List.copyOf(pendingOwnerOperations);
        }
        return awaitFailures(unresolvedOperations).thenCompose(ignored -> operations.submit(playerId, () -> {
            synchronized (monitor) {
                var cached = players.get(playerId);
                if (cached != null) {
                    release(playerId, cached, sessionToken);
                }
            }
            return CompletableFuture.completedFuture(null);
        }));
    }

    /**
     * Waits for every write accepted before this call. Writes are already write-through, so this
     * method does not issue duplicate repository operations or evict online-player snapshots.
     */
    public CompletableFuture<Void> flushAll() {
        List<CompletableFuture<?>> outstanding;
        synchronized (monitor) {
            outstanding = new ArrayList<>(pendingOwnerOperations);
        }
        outstanding.add(operations.awaitAll());
        return awaitFailures(outstanding).thenCompose(PlayerDataCache::reportFailures);
    }

    /** Returns whether the player's complete snapshot is currently cached. */
    public boolean isLoaded(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (monitor) {
            return players.containsKey(playerId);
        }
    }

    /** Returns the number of online-player snapshots retained by this cache. */
    public int loadedPlayerCount() {
        synchronized (monitor) {
            return players.size();
        }
    }

    /** Returns an immutable profile snapshot only when the player is currently loaded. */
    public Optional<PlayerProfile> loadedProfile(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CacheLookup<PlayerProfile> lookup = findProfile(playerId);
        return lookup.hit() ? lookup.value() : Optional.empty();
    }

    /** Returns a pet only when its owner's complete snapshot is currently loaded. */
    public Optional<OwnedPet> loadedPet(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        CacheLookup<OwnedPet> lookup = findPet(petId);
        return lookup.hit() ? lookup.value() : Optional.empty();
    }

    /** Returns an immutable owned-pet snapshot only when the player is currently loaded. */
    public Optional<List<OwnedPet>> loadedPets(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        CacheLookup<List<OwnedPet>> lookup = findPetsByOwner(playerId);
        return lookup.hit() ? lookup.value() : Optional.empty();
    }

    /** Returns tree progress only when its pet owner's complete snapshot is currently loaded. */
    public Optional<TreeProgress> loadedTreeProgress(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        CacheLookup<TreeProgress> lookup = findTreeProgress(petId);
        return lookup.hit() ? lookup.value() : Optional.empty();
    }

    CacheLookup<PlayerProfile> findProfile(PlayerId playerId) {
        synchronized (monitor) {
            var data = players.get(playerId);
            return data == null ? CacheLookup.miss() : CacheLookup.hit(data.profile());
        }
    }

    CacheLookup<List<OwnedPet>> findPetsByOwner(PlayerId playerId) {
        synchronized (monitor) {
            var data = players.get(playerId);
            return data == null ? CacheLookup.miss() : CacheLookup.hit(data.profile().ownedPets());
        }
    }

    CacheLookup<OwnedPet> findPet(PetId petId) {
        synchronized (monitor) {
            var owner = petOwners.get(petId);
            if (owner == null) {
                return CacheLookup.miss();
            }
            var data = players.get(owner);
            if (data == null || !data.knownPetIds().contains(petId)) {
                return CacheLookup.miss();
            }
            return CacheLookup.hitOptional(findPet(data.profile(), petId));
        }
    }

    CacheLookup<TreeProgress> findTreeProgress(PetId petId) {
        synchronized (monitor) {
            var owner = petOwners.get(petId);
            if (owner == null) {
                return CacheLookup.miss();
            }
            var data = players.get(owner);
            if (data == null || !data.knownPetIds().contains(petId)) {
                return CacheLookup.miss();
            }
            return CacheLookup.hitOptional(data.treeProgress().getOrDefault(petId, Optional.empty()));
        }
    }

    CompletableFuture<Integer> resetForDeath(
            PlayerId owner, TreeProgress zeroed, Supplier<CompletableFuture<Integer>> durableReset
    ) {
        return operations.submit(owner, () -> durableReset.get().thenApply(balance -> {
            synchronized (monitor) {
                var current = players.get(owner);
                if (current != null) {
                    var progress = new LinkedHashMap<>(current.treeProgress());
                    progress.put(zeroed.petId(), Optional.of(zeroed));
                    replace(owner, current, current.withProfile(current.profile().withSkillPointBalance(balance))
                            .withTreeProgress(progress));
                }
            }
            return balance;
        }));
    }

    CompletableFuture<Void> saveProfile(PlayerProfile profile, Supplier<CompletableFuture<Void>> durableWrite) {
        Objects.requireNonNull(profile, "profile");
        return operations.submit(profile.playerId(), () -> writeThrough(() -> {
            var current = players.get(profile.playerId());
            if (current == null) {
                return CacheChange.none();
            }
            var changedProfile = current.profile().withSkillPointBalance(profile.skillPointBalance());
            return replace(profile.playerId(), current, current.withProfile(changedProfile));
        }, durableWrite));
    }

    CompletableFuture<Void> savePet(OwnedPet pet, Supplier<CompletableFuture<Void>> durableWrite) {
        Objects.requireNonNull(pet, "pet");
        return operations.submit(pet.owner(), () -> resolveOwner(pet.id()).thenCompose(storedOwner -> {
            if (storedOwner.isPresent() && !storedOwner.orElseThrow().equals(pet.owner())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "cannot change owner of pet " + pet.id() + " from "
                                + storedOwner.orElseThrow() + " to " + pet.owner()));
            }
            return writeThrough(() -> addOrReplacePet(pet), durableWrite);
        }));
    }

    CompletableFuture<Boolean> revivePet(OwnedPet pet, Supplier<CompletableFuture<Boolean>> durableWrite) {
        return operations.submit(pet.owner(), () -> durableWrite.get().thenApply(revived -> {
            if (revived) {
                addOrReplacePet(pet);
            }
            return revived;
        }));
    }

    CompletableFuture<Void> deletePet(PetId petId, Supplier<CompletableFuture<Void>> durableWrite) {
        Objects.requireNonNull(petId, "petId");
        return submitForPetOwner(petId, owner -> () -> writeThrough(() -> deletePet(owner, petId), durableWrite),
                durableWrite);
    }

    CompletableFuture<Void> saveTreeProgress(
            TreeProgress progress, Supplier<CompletableFuture<Void>> durableWrite
    ) {
        Objects.requireNonNull(progress, "progress");
        return submitForPetOwner(progress.petId(),
                owner -> () -> writeThrough(() -> saveTreeProgress(owner, progress), durableWrite),
                durableWrite);
    }

    /**
     * Runs a pet-scoped write in its owner's operation sequence.
     *
     * <p>When the owner is cached, the write is enqueued immediately, so it is always ordered before
     * any later {@link #unload(PlayerId)} of that owner. Otherwise the owner is resolved
     * asynchronously (the player is offline); such lookups are tracked so {@link #flushAll()} waits
     * for them.</p>
     */
    private CompletableFuture<Void> submitForPetOwner(
            PetId petId,
            Function<PlayerId, Supplier<CompletableFuture<Void>>> cachedWrite,
            Supplier<CompletableFuture<Void>> durableWrite
    ) {
        PlayerId cachedOwner;
        var lifecycle = new CompletableFuture<Void>();
        synchronized (monitor) {
            cachedOwner = petOwners.get(petId);
            if (cachedOwner == null) {
                pendingOwnerOperations.add(lifecycle);
            }
        }
        if (cachedOwner != null) {
            return operations.submit(cachedOwner, cachedWrite.apply(cachedOwner));
        }
        var routed = resolveOwner(petId).thenCompose(owner -> owner
                .map(playerId -> operations.submit(playerId, cachedWrite.apply(playerId)))
                .orElseGet(() -> invoke(durableWrite)));
        routed.whenComplete((ignored, failure) -> {
            if (failure == null) {
                lifecycle.complete(null);
            } else {
                lifecycle.completeExceptionally(unwrap(failure));
            }
            synchronized (monitor) {
                pendingOwnerOperations.remove(lifecycle);
            }
        });
        return detachedView(lifecycle);
    }

    private CompletableFuture<PlayerProfile> loadFromDelegates(PlayerId playerId, Long sessionToken) {
        CompletableFuture<Optional<PlayerProfile>> profileFuture = invoke(() -> playerDelegate.findById(playerId));
        CompletableFuture<List<OwnedPet>> petsFuture = invoke(() -> petDelegate.findByOwner(playerId));
        return profileFuture.thenCombine(petsFuture, (storedProfile, pets) -> assemble(playerId, storedProfile, pets))
                .thenCompose(profile -> loadProgress(profile).thenApply(progress -> {
                    install(profile, progress, sessionToken);
                    return profile;
                }));
    }

    private CompletableFuture<Map<PetId, Optional<TreeProgress>>> loadProgress(PlayerProfile profile) {
        var progress = new LinkedHashMap<PetId, Optional<TreeProgress>>();
        var loads = profile.ownedPets().stream()
                .map(pet -> invoke(() -> treeDelegate.load(pet.id())).thenAccept(value -> {
                    value.ifPresent(tree -> requirePetId(pet.id(), tree));
                    synchronized (progress) {
                        progress.put(pet.id(), value);
                    }
                }))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(loads).thenApply(ignored -> {
            var ordered = new LinkedHashMap<PetId, Optional<TreeProgress>>();
            for (OwnedPet pet : profile.ownedPets()) {
                ordered.put(pet.id(), progress.getOrDefault(pet.id(), Optional.empty()));
            }
            return Map.copyOf(ordered);
        });
    }

    private void install(
            PlayerProfile profile,
            Map<PetId, Optional<TreeProgress>> progress,
            Long sessionToken
    ) {
        var knownPetIds = Set.copyOf(progress.keySet());
        Set<Long> sessions = sessionToken == null ? Set.of() : Set.of(sessionToken);
        var data = new CachedPlayerData(profile, knownPetIds, progress, sessions);
        synchronized (monitor) {
            players.put(profile.playerId(), data);
            for (PetId petId : knownPetIds) {
                petOwners.put(petId, profile.playerId());
            }
        }
    }

    private void evict(PlayerId playerId, CachedPlayerData snapshot) {
        if (!players.remove(playerId, snapshot)) {
            return;
        }
        for (PetId petId : snapshot.knownPetIds()) {
            petOwners.remove(petId, playerId);
        }
    }

    private void release(PlayerId playerId, CachedPlayerData snapshot, Long sessionToken) {
        if (sessionToken == null) {
            evict(playerId, snapshot);
            return;
        }
        if (!snapshot.sessions().contains(sessionToken)) {
            return;
        }
        var remainingSessions = new HashSet<>(snapshot.sessions());
        remainingSessions.remove(sessionToken);
        if (remainingSessions.isEmpty()) {
            evict(playerId, snapshot);
        } else {
            players.replace(playerId, snapshot, snapshot.withSessions(remainingSessions));
        }
    }

    private CompletableFuture<Optional<PlayerId>> resolveOwner(PetId petId) {
        synchronized (monitor) {
            var cachedOwner = petOwners.get(petId);
            if (cachedOwner != null) {
                return CompletableFuture.completedFuture(Optional.of(cachedOwner));
            }
        }
        return invoke(() -> petDelegate.findById(petId)).thenApply(storedPet -> storedPet.map(OwnedPet::owner));
    }

    private CacheChange addOrReplacePet(OwnedPet pet) {
        var current = players.get(pet.owner());
        if (current == null) {
            return CacheChange.none();
        }

        var pets = new ArrayList<>(current.profile().ownedPets());
        int existingIndex = indexOf(pets, pet.id());
        boolean duplicateVariant = pets.stream()
                .anyMatch(existing -> !existing.id().equals(pet.id()) && existing.variant().equals(pet.variant()));
        if (duplicateVariant) {
            throw new DuplicatePetVariantException(pet.owner(), pet.variant());
        }
        if (existingIndex < 0) {
            pets.add(pet);
        } else {
            pets.set(existingIndex, pet);
        }
        var profile = current.profile().withOwnedPets(pets);
        var knownPetIds = new HashSet<>(current.knownPetIds());
        knownPetIds.add(pet.id());
        var progress = new LinkedHashMap<>(current.treeProgress());
        progress.putIfAbsent(pet.id(), Optional.empty());
        var changed = new CachedPlayerData(profile, knownPetIds, progress, current.sessions());
        petOwners.put(pet.id(), pet.owner());
        return replace(pet.owner(), current, changed);
    }

    private CacheChange deletePet(PlayerId owner, PetId petId) {
        var current = players.get(owner);
        if (current == null || !current.knownPetIds().contains(petId)) {
            return CacheChange.none();
        }
        var pets = current.profile().ownedPets().stream()
                .filter(pet -> !pet.id().equals(petId))
                .toList();
        var progress = new LinkedHashMap<>(current.treeProgress());
        progress.put(petId, Optional.empty());
        var changed = new CachedPlayerData(
                current.profile().withOwnedPets(pets), current.knownPetIds(), progress, current.sessions());
        return replace(owner, current, changed);
    }

    private CacheChange saveTreeProgress(PlayerId owner, TreeProgress progress) {
        var current = players.get(owner);
        if (current == null || findPet(current.profile(), progress.petId()).isEmpty()) {
            return CacheChange.none();
        }
        var treeProgress = new LinkedHashMap<>(current.treeProgress());
        treeProgress.put(progress.petId(), Optional.of(progress));
        return replace(owner, current, current.withTreeProgress(treeProgress));
    }

    private CompletableFuture<Void> writeThrough(
            Supplier<CacheChange> mutation, Supplier<CompletableFuture<Void>> durableWrite
    ) {
        Objects.requireNonNull(durableWrite, "durableWrite");
        CacheChange change;
        try {
            synchronized (monitor) {
                change = Objects.requireNonNull(mutation.get(), "cache mutation");
            }
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }

        CompletableFuture<Void> write = invoke(durableWrite);
        return write.handle((ignored, failure) -> {
            if (failure == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            synchronized (monitor) {
                change.rollback();
            }
            return CompletableFuture.<Void>failedFuture(unwrap(failure));
        }).thenCompose(result -> result);
    }

    private CacheChange replace(PlayerId playerId, CachedPlayerData before, CachedPlayerData after) {
        players.put(playerId, after);
        return () -> {
            if (players.get(playerId) == after) {
                players.put(playerId, before);
                for (PetId petId : after.knownPetIds()) {
                    if (!before.knownPetIds().contains(petId)) {
                        petOwners.remove(petId, playerId);
                    }
                }
            }
        };
    }

    private static PlayerProfile assemble(
            PlayerId requestedId, Optional<PlayerProfile> storedProfile, List<OwnedPet> pets
    ) {
        Objects.requireNonNull(storedProfile, "profile lookup result");
        Objects.requireNonNull(pets, "pet lookup result");
        int balance = storedProfile.map(profile -> {
            if (!profile.playerId().equals(requestedId)) {
                throw new IllegalStateException("player repository returned a different player id");
            }
            return profile.skillPointBalance();
        }).orElse(0);
        for (OwnedPet pet : pets) {
            if (!pet.owner().equals(requestedId)) {
                throw new IllegalStateException("pet repository returned a pet owned by another player");
            }
        }
        return new PlayerProfile(requestedId, balance, pets);
    }

    private static void requirePetId(PetId requestedId, TreeProgress progress) {
        if (!progress.petId().equals(requestedId)) {
            throw new IllegalStateException("tree progress repository returned a different pet id");
        }
    }

    private static Optional<OwnedPet> findPet(PlayerProfile profile, PetId petId) {
        return profile.ownedPets().stream().filter(pet -> pet.id().equals(petId)).findFirst();
    }

    private static int indexOf(List<OwnedPet> pets, PetId petId) {
        for (int index = 0; index < pets.size(); index++) {
            if (pets.get(index).id().equals(petId)) {
                return index;
            }
        }
        return -1;
    }

    private static <T> CompletableFuture<T> invoke(Supplier<CompletableFuture<T>> operation) {
        try {
            return Objects.requireNonNull(operation.get(), "repository future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static <T> CompletableFuture<T> detachedView(CompletableFuture<T> source) {
        var view = new CompletableFuture<T>();
        source.whenComplete((value, failure) -> {
            if (failure == null) {
                view.complete(value);
            } else {
                view.completeExceptionally(unwrap(failure));
            }
        });
        return view;
    }

    private static CompletableFuture<List<Throwable>> awaitFailures(
            List<? extends CompletableFuture<?>> futures
    ) {
        var outcomes = futures.stream()
                .map(future -> future.handle((ignored, failure) -> Optional.ofNullable(failure).map(
                        PlayerDataCache::unwrap)))
                .toList();
        return CompletableFuture.allOf(outcomes.toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> outcomes.stream()
                        .map(CompletableFuture::join)
                        .flatMap(Optional::stream)
                        .toList());
    }

    private static CompletableFuture<Void> reportFailures(List<Throwable> failures) {
        var uniqueFailures = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        uniqueFailures.addAll(failures);
        if (uniqueFailures.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        if (uniqueFailures.size() == 1) {
            return CompletableFuture.failedFuture(uniqueFailures.iterator().next());
        }

        var iterator = uniqueFailures.iterator();
        var aggregate = new IllegalStateException(
                "multiple cache operations failed (" + uniqueFailures.size() + ")", iterator.next());
        iterator.forEachRemaining(aggregate::addSuppressed);
        return CompletableFuture.failedFuture(aggregate);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record CachedPlayerData(
            PlayerProfile profile,
            Set<PetId> knownPetIds,
            Map<PetId, Optional<TreeProgress>> treeProgress,
            Set<Long> sessions
    ) {
        private CachedPlayerData {
            Objects.requireNonNull(profile, "profile");
            knownPetIds = Set.copyOf(knownPetIds);
            treeProgress = Map.copyOf(treeProgress);
            sessions = Set.copyOf(sessions);
        }

        private CachedPlayerData withProfile(PlayerProfile changedProfile) {
            return new CachedPlayerData(changedProfile, knownPetIds, treeProgress, sessions);
        }

        private CachedPlayerData withTreeProgress(Map<PetId, Optional<TreeProgress>> changedProgress) {
            return new CachedPlayerData(profile, knownPetIds, changedProgress, sessions);
        }

        private CachedPlayerData withSession(long sessionToken) {
            var changedSessions = new HashSet<>(sessions);
            changedSessions.add(sessionToken);
            return withSessions(changedSessions);
        }

        private CachedPlayerData withSessions(Set<Long> changedSessions) {
            return new CachedPlayerData(profile, knownPetIds, treeProgress, changedSessions);
        }
    }

    private interface CacheChange {
        void rollback();

        static CacheChange none() {
            return () -> { };
        }
    }
}
