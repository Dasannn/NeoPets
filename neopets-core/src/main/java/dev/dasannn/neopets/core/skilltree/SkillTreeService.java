package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.NodeActivated;
import dev.dasannn.neopets.core.event.NodeUnlocked;
import dev.dasannn.neopets.core.event.SkillPointsChanged;
import dev.dasannn.neopets.core.event.TreeReset;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Purchases, activates, and resets skill-tree levels for owned pets.
 *
 * <p>Every operation for one player is sequenced through a queue shared with skill-point mutations.
 * Cross-repository changes use compensation when a later persistence step fails.</p>
 */
public final class SkillTreeService {
    private final PetRepository petRepository;
    private final TreeProgressRepository progressRepository;
    private final SkillTreeRegistry treeRegistry;
    private final Function<VariantId, Optional<PetTypeId>> variantTypeResolver;
    private final Economy economy;
    private final SkillPointService skillPointService;
    private final RankLimitsResolver rankLimitsResolver;
    private final DomainEventBus eventBus;
    private final PlayerOperationQueue operations;

    /** Creates a service with persistence and external effects supplied as ports. */
    public SkillTreeService(
            PetRepository petRepository,
            TreeProgressRepository progressRepository,
            SkillTreeRegistry treeRegistry,
            Function<VariantId, Optional<PetTypeId>> variantTypeResolver,
            Economy economy,
            SkillPointService skillPointService,
            RankLimitsResolver rankLimitsResolver,
            DomainEventBus eventBus,
            PlayerOperationQueue operations
    ) {
        this.petRepository = Objects.requireNonNull(petRepository, "petRepository");
        this.progressRepository = Objects.requireNonNull(progressRepository, "progressRepository");
        this.treeRegistry = Objects.requireNonNull(treeRegistry, "treeRegistry");
        this.variantTypeResolver = Objects.requireNonNull(variantTypeResolver, "variantTypeResolver");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.skillPointService = Objects.requireNonNull(skillPointService, "skillPointService");
        this.rankLimitsResolver = Objects.requireNonNull(rankLimitsResolver, "rankLimitsResolver");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.operations = Objects.requireNonNull(operations, "operations");
        if (!skillPointService.uses(operations)) {
            throw new IllegalArgumentException("skill-point and skill-tree services must share one player queue");
        }
    }

    /** Purchases the next level of an attribute with money. */
    public CompletableFuture<UnlockResult> unlock(PlayerId playerId, PetId petId, String attributeId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(attributeId, "attributeId");
        return operations.submit(playerId, () -> safely(
                () -> unlockUnsequenced(playerId, petId, attributeId), UnlockResult.Failed::new));
    }

    /** Activates the next purchased but inactive level with the player's skill points. */
    public CompletableFuture<ActivateResult> activate(PlayerId playerId, PetId petId, String attributeId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(attributeId, "attributeId");
        return operations.submit(playerId, () -> safely(
                () -> activateUnsequenced(playerId, petId, attributeId), ActivateResult.Failed::new));
    }

    /** Resets every level, optionally returning all assigned skill points; money is never returned. */
    public CompletableFuture<ResetResult> reset(PlayerId playerId, PetId petId, boolean refundSkillPoints) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return operations.submit(playerId, () -> safely(
                () -> resetUnsequenced(playerId, petId, refundSkillPoints), ResetResult.Failed::new));
    }

    /** Resets death progress with an atomic, repeatable durable refund and death checkpoint. */
    public CompletableFuture<ResetResult> resetForDeath(PlayerId playerId, PetId petId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        return operations.submit(playerId, () -> safely(
                () -> resetUnsequenced(playerId, petId, true, true), ResetResult.Failed::new));
    }

    private CompletableFuture<UnlockResult> unlockUnsequenced(
            PlayerId playerId, PetId petId, String attributeId
    ) {
        return petRepository.findById(petId).thenCompose(foundPet -> {
            var ownershipFailure = unlockOwnershipFailure(foundPet, playerId, petId);
            if (ownershipFailure.isPresent()) {
                return CompletableFuture.completedFuture(ownershipFailure.orElseThrow());
            }

            OwnedPet pet = foundPet.orElseThrow();
            var tree = treeFor(pet);
            var attribute = tree.flatMap(value -> findAttribute(value, attributeId));
            if (attribute.isEmpty()) {
                return CompletableFuture.completedFuture(new UnlockResult.UnknownAttribute(attributeId));
            }

            SkillTreeDefinition definition = tree.orElseThrow();
            AttributeDefinition attributeDefinition = attribute.orElseThrow();
            return progressRepository.load(petId).thenCompose(stored -> {
                TreeProgress progress = progress(stored, petId, definition);
                AttributeLevelProgress levelProgress = progress.attributes().get(attributeDefinition.id());
                if (levelProgress.unlockedLevel() >= attributeDefinition.maxLevel()) {
                    return CompletableFuture.completedFuture(
                            new UnlockResult.MaxLevel(attributeDefinition.id(), attributeDefinition.maxLevel()));
                }

                int nodeLimit = rankLimitsResolver.resolve(playerId).maxNodesPerPet();
                if (progress.unlockedNodes() >= nodeLimit) {
                    return CompletableFuture.completedFuture(new UnlockResult.NodeLimitReached(nodeLimit));
                }

                int nextLevel = levelProgress.unlockedLevel() + 1;
                BigDecimal moneyCost = attributeDefinition.unlockMoneyCosts().get(nextLevel - 1);
                double economyCost = finiteDouble(moneyCost);
                TreeProgress changed = progress.withUnlockedLevel(attributeDefinition.id(), nextLevel);
                if (moneyCost.signum() == 0) {
                    return saveUnlocked(
                            playerId,
                            attributeDefinition.id(),
                            nextLevel,
                            moneyCost,
                            economyCost,
                            changed);
                }
                return economy.has(playerId, economyCost).thenCompose(balanceCheck -> {
                    if (balanceCheck instanceof EconomyResult.Failure failure) {
                        return CompletableFuture.completedFuture(new UnlockResult.Failed(
                                new IllegalStateException(
                                        "economy balance check failed: " + failure.reason())));
                    }
                    double balance = ((EconomyResult.Success) balanceCheck).balance();
                    if (balance < economyCost) {
                        return CompletableFuture.completedFuture(new UnlockResult.InsufficientFunds(moneyCost));
                    }

                    return economy.withdraw(playerId, economyCost).thenCompose(withdrawal -> {
                        if (withdrawal instanceof EconomyResult.Failure failure) {
                            return CompletableFuture.completedFuture(new UnlockResult.Failed(
                                    new IllegalStateException(
                                            "economy withdrawal failed: " + failure.reason())));
                        }

                        return saveUnlocked(
                                playerId,
                                attributeDefinition.id(),
                                nextLevel,
                                moneyCost,
                                economyCost,
                                changed);
                    });
                });
            });
        });
    }

    private CompletableFuture<UnlockResult> saveUnlocked(
            PlayerId playerId,
            String attributeId,
            int level,
            BigDecimal moneyCost,
            double economyCost,
            TreeProgress changed
    ) {
        return saveProgress(changed)
                .<CompletableFuture<UnlockResult>>handle((ignored, failure) -> {
                    if (failure != null) {
                        if (economyCost == 0) {
                            return CompletableFuture.completedFuture(
                                    new UnlockResult.Failed(unwrap(failure)));
                        }
                        return refundAfterFailedSave(playerId, economyCost, failure);
                    }
                    eventBus.publish(new NodeUnlocked(changed.petId(), attributeId, level));
                    return CompletableFuture.completedFuture(new UnlockResult.Unlocked(
                            changed, attributeId, level, moneyCost));
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<UnlockResult> refundAfterFailedSave(
            PlayerId playerId, double economyCost, Throwable saveFailure
    ) {
        Throwable cause = unwrap(saveFailure);
        CompletableFuture<EconomyResult> refund;
        try {
            refund = Objects.requireNonNull(
                    economy.deposit(playerId, economyCost), "economy deposit future");
        } catch (Throwable refundFailure) {
            cause.addSuppressed(unwrap(refundFailure));
            return CompletableFuture.completedFuture(new UnlockResult.Failed(cause));
        }

        return refund.handle((result, refundFailure) -> {
            if (refundFailure != null) {
                cause.addSuppressed(unwrap(refundFailure));
            } else if (result instanceof EconomyResult.Failure businessFailure) {
                cause.addSuppressed(new IllegalStateException(
                        "economy refund failed: " + businessFailure.reason()));
            }
            return (UnlockResult) new UnlockResult.Failed(cause);
        });
    }

    private CompletableFuture<ActivateResult> activateUnsequenced(
            PlayerId playerId, PetId petId, String attributeId
    ) {
        return petRepository.findById(petId).thenCompose(foundPet -> {
            var ownershipFailure = activateOwnershipFailure(foundPet, playerId, petId);
            if (ownershipFailure.isPresent()) {
                return CompletableFuture.completedFuture(ownershipFailure.orElseThrow());
            }

            OwnedPet pet = foundPet.orElseThrow();
            var tree = treeFor(pet);
            var attribute = tree.flatMap(value -> findAttribute(value, attributeId));
            if (attribute.isEmpty()) {
                return CompletableFuture.completedFuture(new ActivateResult.UnknownAttribute(attributeId));
            }

            SkillTreeDefinition definition = tree.orElseThrow();
            AttributeDefinition attributeDefinition = attribute.orElseThrow();
            return progressRepository.load(petId).thenCompose(stored -> {
                TreeProgress progress = progress(stored, petId, definition);
                AttributeLevelProgress levelProgress = progress.attributes().get(attributeDefinition.id());
                if (levelProgress.activeLevel() >= levelProgress.unlockedLevel()) {
                    return CompletableFuture.completedFuture(
                            new ActivateResult.NoUnlockedLevel(attributeDefinition.id()));
                }

                int nextLevel = levelProgress.activeLevel() + 1;
                int skillPointCost = attributeDefinition.skillPointCosts().get(nextLevel - 1);
                int assigned = progress.assignedSkillPoints(definition);
                int skillPointLimit = rankLimitsResolver.resolve(playerId).maxSkillPointsPerPet();
                if ((long) assigned + skillPointCost > skillPointLimit) {
                    return CompletableFuture.completedFuture(
                            new ActivateResult.SkillPointLimitReached(skillPointLimit));
                }

                TreeProgress changed = progress.withActiveLevel(attributeDefinition.id(), nextLevel);
                if (skillPointCost == 0) {
                    return saveActivated(
                            playerId, attributeDefinition.id(), nextLevel, 0, changed, false);
                }
                return skillPointService.balanceUnsequenced(playerId).thenCompose(balance -> {
                    if (balance < skillPointCost) {
                        return CompletableFuture.completedFuture(
                                new ActivateResult.InsufficientSkillPoints(skillPointCost, balance));
                    }
                    return skillPointService.applyDelta(playerId, -skillPointCost).thenCompose(ignored -> saveActivated(
                            playerId, attributeDefinition.id(), nextLevel, skillPointCost, changed, true));
                });
            });
        });
    }

    private CompletableFuture<ActivateResult> saveActivated(
            PlayerId playerId,
            String attributeId,
            int level,
            int skillPointCost,
            TreeProgress changed,
            boolean pointsSpent
    ) {
        return saveProgress(changed).<CompletableFuture<ActivateResult>>handle((ignored, failure) -> {
            if (failure == null) {
                eventBus.publish(new NodeActivated(changed.petId(), attributeId, level));
                return CompletableFuture.completedFuture(
                        new ActivateResult.Activated(changed, attributeId, level, skillPointCost));
            }

            Throwable cause = unwrap(failure);
            if (!pointsSpent) {
                return CompletableFuture.completedFuture(new ActivateResult.Failed(cause));
            }
            return skillPointService.applyDelta(playerId, skillPointCost)
                    .handle((balance, refundFailure) -> {
                        if (refundFailure != null) {
                            cause.addSuppressed(unwrap(refundFailure));
                        }
                        return (ActivateResult) new ActivateResult.Failed(cause);
                    });
        }).thenCompose(Function.identity());
    }

    private CompletableFuture<ResetResult> resetUnsequenced(
            PlayerId playerId, PetId petId, boolean refundSkillPoints
    ) {
        return resetUnsequenced(playerId, petId, refundSkillPoints, false);
    }

    private CompletableFuture<ResetResult> resetUnsequenced(
            PlayerId playerId, PetId petId, boolean refundSkillPoints, boolean death
    ) {
        return petRepository.findById(petId).thenCompose(foundPet -> {
            if (foundPet.isEmpty() || !foundPet.orElseThrow().owner().equals(playerId)) {
                return CompletableFuture.completedFuture(new ResetResult.NotOwned(petId));
            }

            OwnedPet pet = foundPet.orElseThrow();
            SkillTreeDefinition definition = treeFor(pet).orElseThrow(() ->
                    new IllegalStateException("no skill tree is configured for pet variant " + pet.variant()));
            return progressRepository.load(petId).thenCompose(stored -> {
                TreeProgress current = progress(stored, petId, definition);
                TreeProgress zeroed = TreeProgress.empty(petId, definition);
                int refund = refundSkillPoints ? current.assignedSkillPoints(definition) : 0;
                if (death) {
                    return skillPointService.balanceUnsequenced(playerId).thenCompose(before ->
                            progressRepository.resetForDeath(playerId, zeroed, refund).thenCompose(balance -> {
                                if (before != balance) {
                                    eventBus.publish(new SkillPointsChanged(playerId, before, balance));
                                }
                                return completedReset(zeroed, refund);
                            }));
                }
                return saveReset(playerId, current, zeroed, refund);
            });
        });
    }

    private CompletableFuture<ResetResult> saveReset(
            PlayerId playerId, TreeProgress current, TreeProgress zeroed, int refund
    ) {
        return saveProgress(zeroed).thenCompose(ignored -> {
            if (refund == 0) {
                return completedReset(zeroed, refund);
            }

            return skillPointService.applyDelta(playerId, refund)
                    .<CompletableFuture<ResetResult>>handle((balance, failure) -> {
                        if (failure == null) {
                            return completedReset(zeroed, refund);
                        }

                        Throwable cause = unwrap(failure);
                        return saveProgress(current).handle((restored, compensationFailure) -> {
                            if (compensationFailure != null) {
                                cause.addSuppressed(unwrap(compensationFailure));
                            }
                            return (ResetResult) new ResetResult.Failed(cause);
                        });
                    }).thenCompose(Function.identity());
        });
    }

    private CompletableFuture<ResetResult> completedReset(TreeProgress zeroed, int refund) {
        eventBus.publish(new TreeReset(zeroed.petId()));
        return CompletableFuture.completedFuture(new ResetResult.Reset(zeroed, refund));
    }

    private Optional<SkillTreeDefinition> treeFor(OwnedPet pet) {
        Optional<PetTypeId> petType = Objects.requireNonNull(
                variantTypeResolver.apply(pet.variant()), "variantTypeResolver result");
        return petType.flatMap(treeRegistry::findByPetType);
    }

    private CompletableFuture<Void> saveProgress(TreeProgress progress) {
        try {
            return Objects.requireNonNull(progressRepository.save(progress), "progress save future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static Optional<AttributeDefinition> findAttribute(
            SkillTreeDefinition definition, String attributeId
    ) {
        return definition.attributes().stream()
                .filter(attribute -> attribute.id().equals(attributeId))
                .findFirst();
    }

    private static TreeProgress progress(
            Optional<TreeProgress> stored, PetId petId, SkillTreeDefinition definition
    ) {
        if (stored.isEmpty()) {
            return TreeProgress.empty(petId, definition);
        }
        TreeProgress progress = stored.orElseThrow();
        if (!progress.petId().equals(petId)) {
            throw new IllegalStateException("tree progress repository returned a different pet id");
        }
        for (AttributeDefinition attribute : definition.attributes()) {
            AttributeLevelProgress level = progress.attributes().get(attribute.id());
            if (level == null || level.maxLevel() != attribute.maxLevel()) {
                throw new IllegalStateException(
                        "stored progress does not match the definition for attribute " + attribute.id());
            }
        }
        return progress;
    }

    private static Optional<UnlockResult> unlockOwnershipFailure(
            Optional<OwnedPet> foundPet, PlayerId playerId, PetId petId
    ) {
        if (foundPet.isEmpty() || !foundPet.orElseThrow().owner().equals(playerId)) {
            return Optional.of(new UnlockResult.NotOwned(petId));
        }
        OwnedPet pet = foundPet.orElseThrow();
        return switch (pet.status()) {
            case ALIVE -> Optional.empty();
            case DEAD -> Optional.of(new UnlockResult.PetDead(pet));
            case IN_TRANSIT -> Optional.of(new UnlockResult.PetInTransit(pet));
        };
    }

    private static Optional<ActivateResult> activateOwnershipFailure(
            Optional<OwnedPet> foundPet, PlayerId playerId, PetId petId
    ) {
        if (foundPet.isEmpty() || !foundPet.orElseThrow().owner().equals(playerId)) {
            return Optional.of(new ActivateResult.NotOwned(petId));
        }
        OwnedPet pet = foundPet.orElseThrow();
        return switch (pet.status()) {
            case ALIVE -> Optional.empty();
            case DEAD -> Optional.of(new ActivateResult.PetDead(pet));
            case IN_TRANSIT -> Optional.of(new ActivateResult.PetInTransit(pet));
        };
    }

    private static double finiteDouble(BigDecimal amount) {
        double value = amount.doubleValue();
        if (!Double.isFinite(value)) {
            throw new IllegalStateException("money cost is outside the economy's supported range");
        }
        return value;
    }

    private static <T> CompletableFuture<T> safely(
            FutureSupplier<T> operation, Function<Throwable, T> failureResult
    ) {
        try {
            return Objects.requireNonNull(operation.get(), "operation future")
                    .exceptionally(failure -> failureResult.apply(unwrap(failure)));
        } catch (Throwable failure) {
            return CompletableFuture.completedFuture(failureResult.apply(unwrap(failure)));
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface FutureSupplier<T> {
        CompletableFuture<T> get();
    }
}
