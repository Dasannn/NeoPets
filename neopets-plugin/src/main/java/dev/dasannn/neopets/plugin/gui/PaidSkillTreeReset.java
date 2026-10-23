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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

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
            return invokeReset(playerId, petId).handle((result, failure) -> failure == null
                    ? new PaidResetResult.Completed(result)
                    : new PaidResetResult.Failed(unwrap(failure)));
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
                return resetAfterWithdrawal(playerId, petId, cost);
            });
        }).exceptionally(PaidResetResult.Failed::new);
    }

    private CompletableFuture<PaidResetResult> resetAfterWithdrawal(
            PlayerId playerId, PetId petId, double cost
    ) {
        return invokeReset(playerId, petId)
                .<CompletableFuture<PaidResetResult>>handle((result, failure) -> {
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        return refund(playerId, cost, cause,
                                new PaidResetResult.Failed(cause));
                    }
                    if (result instanceof ResetResult.Reset) {
                        return CompletableFuture.completedFuture(new PaidResetResult.Completed(result));
                    }
                    return refund(playerId, cost, null, new PaidResetResult.Completed(result));
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<ResetResult> invokeReset(PlayerId playerId, PetId petId) {
        try {
            return Objects.requireNonNull(
                    resetOperation.apply(playerId, petId), "reset operation future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<PaidResetResult> refund(
            PlayerId playerId,
            double cost,
            Throwable originalFailure,
            PaidResetResult refundedResult
    ) {
        CompletableFuture<EconomyResult> refund;
        try {
            refund = Objects.requireNonNull(
                    economy.deposit(playerId, cost), "economy deposit future");
        } catch (Throwable refundFailure) {
            return CompletableFuture.completedFuture(refundFailure(
                    originalFailure, unwrap(refundFailure)));
        }
        return refund.handle((result, failure) -> {
            if (failure != null) {
                return refundFailure(originalFailure, unwrap(failure));
            }
            if (result instanceof EconomyResult.Failure rejected) {
                return refundFailure(originalFailure, new IllegalStateException(
                        "economy reset refund failed: " + rejected.reason()));
            }
            return refundedResult;
        });
    }

    private static PaidResetResult refundFailure(Throwable originalFailure, Throwable refundFailure) {
        if (originalFailure != null) {
            originalFailure.addSuppressed(refundFailure);
            return new PaidResetResult.Failed(originalFailure);
        }
        return new PaidResetResult.Failed(refundFailure);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static double finiteDouble(BigDecimal value) {
        double result = value.doubleValue();
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("configuredCost exceeds the economy range");
        }
        return result;
    }
}
