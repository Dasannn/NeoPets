package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/** Charges the configured reset price, invokes the core reset, and compensates failed resets. */
public final class PaidSkillTreeReset {
    private final Economy economy;
    private final BiFunction<PlayerId, PetId, CompletableFuture<ResetResult>> resetOperation;

    public PaidSkillTreeReset(Economy economy, SkillTreeService skillTrees) {
        this.economy = Objects.requireNonNull(economy, "economy");
        Objects.requireNonNull(skillTrees, "skillTrees");
        this.resetOperation = (playerId, petId) -> skillTrees.reset(playerId, petId, true);
    }

    PaidSkillTreeReset(
            Economy economy,
            BiFunction<PlayerId, PetId, CompletableFuture<ResetResult>> resetOperation
    ) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.resetOperation = Objects.requireNonNull(resetOperation, "resetOperation");
    }

    /** Resets with SP refund after a successful payment, refunding payment on any non-success. */
    public CompletableFuture<PaidResetResult> reset(
            PlayerId playerId, PetId petId, BigDecimal configuredCost
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(configuredCost, "configuredCost");
        if (configuredCost.signum() < 0) {
            throw new IllegalArgumentException("configuredCost must not be negative");
        }
        double cost = finiteDouble(configuredCost);
        if (cost == 0) {
            return resetOperation.apply(playerId, petId).thenApply(PaidResetResult.Completed::new);
        }
        return economy.has(playerId, cost).thenCompose(check -> {
            if (check instanceof EconomyResult.Failure failure) {
                return CompletableFuture.completedFuture(new PaidResetResult.Failed(
                        new IllegalStateException("economy balance check failed: " + failure.reason())));
            }
            if (((EconomyResult.Success) check).balance() < cost) {
                return CompletableFuture.completedFuture(new PaidResetResult.InsufficientFunds(configuredCost));
            }
            return economy.withdraw(playerId, cost).thenCompose(withdrawal -> {
                if (withdrawal instanceof EconomyResult.Failure failure) {
                    return CompletableFuture.completedFuture(new PaidResetResult.Failed(
                            new IllegalStateException("economy withdrawal failed: " + failure.reason())));
                }
                return resetOperation.apply(playerId, petId).thenCompose(result -> {
                    if (result instanceof ResetResult.Reset) {
                        return CompletableFuture.completedFuture(new PaidResetResult.Completed(result));
                    }
                    return economy.deposit(playerId, cost).handle((refund, refundFailure) -> {
                        if (refundFailure != null) {
                            return (PaidResetResult) new PaidResetResult.Failed(refundFailure);
                        }
                        if (refund instanceof EconomyResult.Failure failure) {
                            return (PaidResetResult) new PaidResetResult.Failed(new IllegalStateException(
                                    "economy reset refund failed: " + failure.reason()));
                        }
                        return new PaidResetResult.Completed(result);
                    });
                });
            });
        }).exceptionally(PaidResetResult.Failed::new);
    }

    private static double finiteDouble(BigDecimal value) {
        double result = value.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("configuredCost exceeds the economy range");
        }
        return result;
    }
}
