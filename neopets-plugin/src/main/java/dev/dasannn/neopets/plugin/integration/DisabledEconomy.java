package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Economy fallback used when Vault or an economy provider is unavailable. */
public final class DisabledEconomy implements Economy {
    /** Stable failure reason returned by every attempted balance mutation. */
    public static final String UNAVAILABLE_REASON = "Economy unavailable";

    @Override
    public CompletableFuture<EconomyResult> has(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        validateAmount(amount);
        return CompletableFuture.completedFuture(unavailable());
    }

    @Override
    public CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        validateAmount(amount);
        return CompletableFuture.completedFuture(unavailable());
    }

    @Override
    public CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        validateAmount(amount);
        return CompletableFuture.completedFuture(unavailable());
    }

    private static EconomyResult unavailable() {
        return new EconomyResult.Failure(UNAVAILABLE_REASON);
    }

    private static void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be finite and greater than zero");
        }
    }
}
