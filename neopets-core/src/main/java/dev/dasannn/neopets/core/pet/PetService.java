package dev.dasannn.neopets.core.pet;

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
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.RandomSource;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Coordinates pet grants and active-pet lifecycle changes.
 *
 * <p>Operations for one player are serialized so concurrent grants cannot award the same
 * variant twice and concurrent summons cannot exceed that player's rank limit.</p>
 */
public final class PetService {
    private final VariantCatalog variantCatalog;
    private final PetRepository petRepository;
    private final PlayerRepository playerRepository;
    private final PetEntityGateway entityGateway;
    private final RankLimitsResolver rankLimitsResolver;
    private final DomainEventBus eventBus;
    private final RandomSource randomSource;
    private final Clock clock;
    private final AtomicLong petIdSequence = new AtomicLong();
    private final ConcurrentHashMap<PlayerId, CompletableFuture<Void>> operationTails = new ConcurrentHashMap<>();

    /** Creates a pet service with all persistence and external effects supplied as ports. */
    public PetService(
            VariantCatalog variantCatalog,
            PetRepository petRepository,
            PlayerRepository playerRepository,
            PetEntityGateway entityGateway,
            RankLimitsResolver rankLimitsResolver,
            DomainEventBus eventBus,
            RandomSource randomSource,
            Clock clock
    ) {
        this.variantCatalog = Objects.requireNonNull(variantCatalog, "variantCatalog");
        this.petRepository = Objects.requireNonNull(petRepository, "petRepository");
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.entityGateway = Objects.requireNonNull(entityGateway, "entityGateway");
        this.rankLimitsResolver = Objects.requireNonNull(rankLimitsResolver, "rankLimitsResolver");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.randomSource = Objects.requireNonNull(randomSource, "randomSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Gives one weighted-random unowned variant from the requested identifiers.
     * Unknown identifiers reject the entire request without making a grant.
     */
    public CompletableFuture<GiveResult> give(PlayerId playerId, List<VariantId> variantIds) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(variantIds, "variantIds");

        var candidates = new ArrayList<PetVariant>();
        for (VariantId variantId : new LinkedHashSet<>(variantIds)) {
            Objects.requireNonNull(variantId, "variantIds must not contain null");
            var variant = variantCatalog.findById(variantId);
            if (variant.isEmpty()) {
                return CompletableFuture.completedFuture(new GiveResult.UnknownVariant(variantId));
            }
            candidates.add(variant.orElseThrow());
        }
        return serialized(playerId, () -> giveFromCandidates(playerId, candidates));
    }

    /** Gives one weighted-random unowned variant from the whole catalog or one pet type. */
    public CompletableFuture<GiveResult> giveRandom(PlayerId playerId, Optional<PetTypeId> typeId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(typeId, "typeId");
        List<PetVariant> candidates = typeId
                .map(variantCatalog::findByType)
                .orElseGet(variantCatalog::findAll);
        return serialized(playerId, () -> giveFromCandidates(playerId, candidates));
    }

    /** Gives the requested variant, unless it is unknown or already owned. */
    public CompletableFuture<GiveResult> giveSpecific(PlayerId playerId, VariantId variantId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(variantId, "variantId");
        var variant = variantCatalog.findById(variantId);
        if (variant.isEmpty()) {
            return CompletableFuture.completedFuture(new GiveResult.UnknownVariant(variantId));
        }
        return serialized(playerId, () -> loadProfile(playerId).thenCompose(profile -> {
            if (profile.ownsVariant(variantId)) {
                return CompletableFuture.completedFuture(new GiveResult.AlreadyOwned(variantId));
            }
            return grant(profile, variant.orElseThrow());
        }));
    }

    /** Summons an alive, inactive owned pet when the player's active limit permits it. */
    public CompletableFuture<SummonResult> summon(PlayerId playerId, PetId petId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return serialized(playerId, () -> petRepository.findById(petId).thenCombine(
                loadProfile(playerId),
                (pet, profile) -> new PetAndProfile(pet, profile)
        ).thenCompose(state -> {
            var pet = state.pet();
            if (pet.isEmpty() || !pet.orElseThrow().owner().equals(playerId)) {
                return CompletableFuture.completedFuture(new SummonResult.NotOwned(petId));
            }
            var ownedPet = pet.orElseThrow();
            if (ownedPet.status() == PetStatus.DEAD) {
                return CompletableFuture.completedFuture(new SummonResult.Dead(ownedPet));
            }
            if (ownedPet.status() == PetStatus.IN_TRANSIT) {
                return CompletableFuture.completedFuture(new SummonResult.InTransit(ownedPet));
            }
            if (ownedPet.active()) {
                return CompletableFuture.completedFuture(new SummonResult.AlreadyActive(ownedPet));
            }

            int maxActivePets = rankLimitsResolver.resolve(playerId).maxActivePets();
            if (state.profile().activePets().size() >= maxActivePets) {
                return CompletableFuture.completedFuture(new SummonResult.LimitReached(maxActivePets));
            }

            var activePet = ownedPet.withActive(true);
            return persistPetAndProfile(activePet, replacePet(state.profile(), activePet)).thenApply(ignored -> {
                entityGateway.spawn(petId);
                eventBus.publish(new PetSummoned(petId, playerId));
                return new SummonResult.Summoned(activePet);
            });
        }));
    }

    /** Dismisses an alive, active owned pet without applying an active-pet limit. */
    public CompletableFuture<DismissResult> dismiss(PlayerId playerId, PetId petId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return serialized(playerId, () -> petRepository.findById(petId).thenCombine(
                loadProfile(playerId),
                (pet, profile) -> new PetAndProfile(pet, profile)
        ).thenCompose(state -> {
            var pet = state.pet();
            if (pet.isEmpty() || !pet.orElseThrow().owner().equals(playerId)) {
                return CompletableFuture.completedFuture(new DismissResult.NotOwned(petId));
            }
            var ownedPet = pet.orElseThrow();
            if (ownedPet.status() == PetStatus.DEAD) {
                return CompletableFuture.completedFuture(new DismissResult.Dead(ownedPet));
            }
            if (ownedPet.status() == PetStatus.IN_TRANSIT) {
                return CompletableFuture.completedFuture(new DismissResult.InTransit(ownedPet));
            }
            if (!ownedPet.active()) {
                return CompletableFuture.completedFuture(new DismissResult.AlreadyInactive(ownedPet));
            }

            var inactivePet = ownedPet.withActive(false);
            return persistPetAndProfile(inactivePet, replacePet(state.profile(), inactivePet)).thenApply(ignored -> {
                entityGateway.despawn(petId);
                eventBus.publish(new PetDismissed(petId, playerId));
                return new DismissResult.Dismissed(inactivePet);
            });
        }));
    }

    private CompletableFuture<GiveResult> giveFromCandidates(PlayerId playerId, List<PetVariant> candidates) {
        return loadProfile(playerId).thenCompose(profile -> {
            var unowned = candidates.stream()
                    .filter(variant -> !profile.ownsVariant(variant.id()))
                    .toList();
            if (unowned.isEmpty()) {
                return CompletableFuture.completedFuture(new GiveResult.AllOwned());
            }
            return grant(profile, chooseWeighted(unowned));
        });
    }

    private CompletableFuture<GiveResult> grant(PlayerProfile profile, PetVariant variant) {
        Instant createdAt = clock.instant();
        var pet = new OwnedPet(
                nextPetId(profile.playerId(), variant.id(), createdAt),
                profile.playerId(),
                variant.id(),
                Optional.empty(),
                PetStatus.ALIVE,
                false,
                createdAt
        );
        var pets = new ArrayList<>(profile.ownedPets());
        pets.add(pet);
        var updatedProfile = profile.withOwnedPets(pets);
        return persistPetAndProfile(pet, updatedProfile).thenApply(ignored -> {
            eventBus.publish(new PetGiven(pet.id(), pet.owner(), pet.variant()));
            return new GiveResult.Granted(pet);
        });
    }

    private PetVariant chooseWeighted(List<PetVariant> candidates) {
        double maximumWeight = candidates.stream().mapToDouble(PetVariant::weight).max().orElseThrow();
        double totalWeight = candidates.stream().mapToDouble(variant -> variant.weight() / maximumWeight).sum();
        double randomValue = randomSource.nextDouble();
        if (randomValue < 0.0 || randomValue >= 1.0 || !Double.isFinite(randomValue)) {
            throw new IllegalStateException("RandomSource returned a value outside [0, 1)");
        }
        double selectedWeight = randomValue * totalWeight;
        double cumulativeWeight = 0.0;
        for (PetVariant candidate : candidates) {
            cumulativeWeight += candidate.weight() / maximumWeight;
            if (selectedWeight < cumulativeWeight) {
                return candidate;
            }
        }
        return candidates.getLast();
    }

    private CompletableFuture<PlayerProfile> loadProfile(PlayerId playerId) {
        return playerRepository.findById(playerId).thenCombine(
                petRepository.findByOwner(playerId),
                (storedProfile, pets) -> new PlayerProfile(
                        playerId,
                        storedProfile.map(PlayerProfile::skillPointBalance).orElse(0),
                        pets
                )
        );
    }

    private CompletableFuture<Void> persistPetAndProfile(OwnedPet pet, PlayerProfile profile) {
        return CompletableFuture.allOf(petRepository.save(pet), playerRepository.save(profile));
    }

    private static PlayerProfile replacePet(PlayerProfile profile, OwnedPet replacement) {
        var pets = profile.ownedPets().stream()
                .map(pet -> pet.id().equals(replacement.id()) ? replacement : pet)
                .toList();
        return profile.withOwnedPets(pets);
    }

    private PetId nextPetId(PlayerId playerId, VariantId variantId, Instant createdAt) {
        String seed = playerId + "\u0000" + variantId + "\u0000" + createdAt + "\u0000" + petIdSequence.getAndIncrement();
        return new PetId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)));
    }

    private <T> CompletableFuture<T> serialized(PlayerId playerId, Supplier<CompletableFuture<T>> action) {
        var result = new CompletableFuture<T>();
        CompletableFuture<Void> tail = operationTails.compute(playerId, (ignored, previous) -> {
            CompletableFuture<Void> ready = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous;
            return ready.handle((value, failure) -> null).thenCompose(value -> {
                CompletableFuture<T> operation;
                try {
                    operation = Objects.requireNonNull(action.get(), "operation future");
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                    return CompletableFuture.completedFuture(null);
                }
                return operation.handle((operationResult, failure) -> {
                    if (failure == null) {
                        result.complete(operationResult);
                    } else {
                        result.completeExceptionally(failure);
                    }
                    return null;
                });
            });
        });
        tail.whenComplete((value, failure) -> operationTails.remove(playerId, tail));
        return result;
    }

    private record PetAndProfile(Optional<OwnedPet> pet, PlayerProfile profile) {
    }
}
