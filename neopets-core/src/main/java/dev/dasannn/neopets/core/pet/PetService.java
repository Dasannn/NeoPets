package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.concurrent.KeyedSequencer;
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
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetIdGenerator;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.RandomSource;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Coordinates pet grants and active-pet lifecycle changes.
 *
 * <p>Operations for one player are serialized so concurrent grants cannot award the same
 * variant twice and concurrent summons cannot exceed that player's rank limit. Repository
 * uniqueness remains the final guard when multiple service instances race.</p>
 */
public final class PetService {
    private final VariantCatalog variantCatalog;
    private final PetRepository petRepository;
    private final PetEntityGateway entityGateway;
    private final RankLimitsResolver rankLimitsResolver;
    private final DomainEventBus eventBus;
    private final RandomSource randomSource;
    private final Clock clock;
    private final PetIdGenerator petIdGenerator;
    private final KeyedSequencer<PlayerId> operationSequencer = new KeyedSequencer<>();

    /**
     * Creates a pet service with all persistence and external effects supplied as ports.
     * New pet identifiers use random UUIDs.
     */
    public PetService(
            VariantCatalog variantCatalog,
            PetRepository petRepository,
            PetEntityGateway entityGateway,
            RankLimitsResolver rankLimitsResolver,
            DomainEventBus eventBus,
            RandomSource randomSource,
            Clock clock
    ) {
        this(
                variantCatalog,
                petRepository,
                entityGateway,
                rankLimitsResolver,
                eventBus,
                randomSource,
                clock,
                PetIdGenerator.randomUuid()
        );
    }

    /** Creates a pet service with an injectable identifier generator for new pet instances. */
    public PetService(
            VariantCatalog variantCatalog,
            PetRepository petRepository,
            PetEntityGateway entityGateway,
            RankLimitsResolver rankLimitsResolver,
            DomainEventBus eventBus,
            RandomSource randomSource,
            Clock clock,
            PetIdGenerator petIdGenerator
    ) {
        this.variantCatalog = Objects.requireNonNull(variantCatalog, "variantCatalog");
        this.petRepository = Objects.requireNonNull(petRepository, "petRepository");
        this.entityGateway = Objects.requireNonNull(entityGateway, "entityGateway");
        this.rankLimitsResolver = Objects.requireNonNull(rankLimitsResolver, "rankLimitsResolver");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.randomSource = Objects.requireNonNull(randomSource, "randomSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.petIdGenerator = Objects.requireNonNull(petIdGenerator, "petIdGenerator");
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
        return serialized(playerId, () -> petRepository.findByOwner(playerId).thenCompose(pets -> {
            if (ownsVariant(pets, variantId)) {
                return CompletableFuture.completedFuture(new GiveResult.AlreadyOwned(variantId));
            }
            return grantSpecific(playerId, variant.orElseThrow());
        }));
    }

    /** Summons an alive, inactive owned pet when the player's active limit permits it. */
    public CompletableFuture<SummonResult> summon(PlayerId playerId, PetId petId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return serialized(playerId, () -> petRepository.findById(petId).thenCombine(
                petRepository.findByOwner(playerId),
                PetAndOwnedPets::new
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
            long activePets = state.ownedPets().stream().filter(OwnedPet::active).count();
            if (activePets >= maxActivePets) {
                return CompletableFuture.completedFuture(new SummonResult.LimitReached(maxActivePets));
            }

            var activePet = ownedPet.withActive(true);
            return applyEntityThenPersist(
                    () -> entityGateway.spawn(petId),
                    () -> entityGateway.despawn(petId),
                    activePet
            ).thenApply(ignored -> {
                eventBus.publish(new PetSummoned(petId, playerId));
                return new SummonResult.Summoned(activePet);
            });
        }));
    }

    /** Dismisses an alive, active owned pet without applying an active-pet limit. */
    public CompletableFuture<DismissResult> dismiss(PlayerId playerId, PetId petId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return serialized(playerId, () -> petRepository.findById(petId).thenCompose(pet -> {
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
            return applyEntityThenPersist(
                    () -> entityGateway.despawn(petId),
                    () -> entityGateway.spawn(petId),
                    inactivePet
            ).thenApply(ignored -> {
                eventBus.publish(new PetDismissed(petId, playerId));
                return new DismissResult.Dismissed(inactivePet);
            });
        }));
    }

    /** Renames an owned pet in the same per-player sequence as lifecycle operations. */
    public CompletableFuture<RenameResult> rename(PlayerId playerId, PetId petId, String name) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(name, "name");
        int length = name.codePointCount(0, name.length());
        if (name.isBlank() || length > 32 || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("name must contain 1 to 32 characters without line breaks");
        }
        return serialized(playerId, () -> petRepository.findById(petId).thenCompose(found -> {
            if (found.isEmpty() || !found.orElseThrow().owner().equals(playerId)) {
                return CompletableFuture.completedFuture(new RenameResult.NotOwned(petId));
            }
            OwnedPet renamed = found.orElseThrow().withCustomName(name);
            return savePet(renamed).thenApply(ignored -> new RenameResult.Renamed(renamed));
        }));
    }

    private CompletableFuture<GiveResult> giveFromCandidates(PlayerId playerId, List<PetVariant> candidates) {
        return petRepository.findByOwner(playerId).thenCompose(pets -> {
            var unowned = candidates.stream()
                    .filter(variant -> !ownsVariant(pets, variant.id()))
                    .toList();
            return grantRandom(playerId, unowned);
        });
    }

    private CompletableFuture<GiveResult> grantRandom(PlayerId playerId, List<PetVariant> candidates) {
        if (candidates.isEmpty()) {
            return CompletableFuture.completedFuture(new GiveResult.AllOwned());
        }

        var selected = chooseWeighted(candidates);
        var pet = newPet(playerId, selected);
        return savePet(pet).<CompletableFuture<GiveResult>>handle((ignored, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(granted(pet));
            }
            Throwable cause = unwrap(failure);
            if (cause instanceof DuplicatePetVariantException duplicate
                    && duplicate.ownerId().equals(playerId)
                    && duplicate.variantId().equals(selected.id())) {
                var remaining = candidates.stream()
                        .filter(candidate -> !candidate.id().equals(selected.id()))
                        .toList();
                return grantRandom(playerId, remaining);
            }
            return CompletableFuture.failedFuture(cause);
        }).thenCompose(result -> result);
    }

    private CompletableFuture<GiveResult> grantSpecific(PlayerId playerId, PetVariant variant) {
        var pet = newPet(playerId, variant);
        return savePet(pet).<CompletableFuture<GiveResult>>handle((ignored, failure) -> {
            if (failure == null) {
                return CompletableFuture.completedFuture(granted(pet));
            }
            Throwable cause = unwrap(failure);
            if (cause instanceof DuplicatePetVariantException duplicate
                    && duplicate.ownerId().equals(playerId)
                    && duplicate.variantId().equals(variant.id())) {
                return CompletableFuture.completedFuture(new GiveResult.AlreadyOwned(variant.id()));
            }
            return CompletableFuture.failedFuture(cause);
        }).thenCompose(result -> result);
    }

    private OwnedPet newPet(PlayerId playerId, PetVariant variant) {
        Instant createdAt = clock.instant();
        return new OwnedPet(
                Objects.requireNonNull(petIdGenerator.nextId(), "generated pet id"),
                playerId,
                variant.id(),
                Optional.empty(),
                PetStatus.ALIVE,
                false,
                createdAt
        );
    }

    private GiveResult granted(OwnedPet pet) {
        eventBus.publish(new PetGiven(pet.id(), pet.owner(), pet.variant()));
        return new GiveResult.Granted(pet);
    }

    private CompletableFuture<Void> savePet(OwnedPet pet) {
        try {
            return Objects.requireNonNull(petRepository.save(pet), "pet save future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<Void> applyEntityThenPersist(
            Supplier<? extends CompletableFuture<Void>> entityAction,
            Supplier<? extends CompletableFuture<Void>> compensation,
            OwnedPet pet
    ) {
        return invokeEntityAction(entityAction).thenCompose(ignored -> savePet(pet)
                .exceptionallyCompose(failure -> compensateAndFail(compensation, unwrap(failure))));
    }

    private CompletableFuture<Void> compensateAndFail(
            Supplier<? extends CompletableFuture<Void>> compensation,
            Throwable persistenceFailure
    ) {
        return invokeEntityAction(compensation).handle((ignored, compensationFailure) -> {
            if (compensationFailure != null) {
                Throwable cause = unwrap(compensationFailure);
                if (cause != persistenceFailure) {
                    persistenceFailure.addSuppressed(cause);
                }
            }
            return persistenceFailure;
        }).thenCompose(CompletableFuture::failedFuture);
    }

    private CompletableFuture<Void> invokeEntityAction(
            Supplier<? extends CompletableFuture<Void>> action
    ) {
        try {
            return Objects.requireNonNull(action.get(), "entity action future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
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

    private static boolean ownsVariant(List<OwnedPet> pets, VariantId variantId) {
        return pets.stream().anyMatch(pet -> pet.variant().equals(variantId));
    }

    private <T> CompletableFuture<T> serialized(PlayerId playerId, Supplier<CompletableFuture<T>> action) {
        return operationSequencer.submit(playerId, action);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record PetAndOwnedPets(Optional<OwnedPet> pet, List<OwnedPet> ownedPets) {
    }
}
