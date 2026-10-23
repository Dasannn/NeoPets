package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.EconomyResult;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

/** Adapts a registered Vault economy provider to the NeoPets economy port. */
public final class VaultEconomy implements dev.dasannn.neopets.core.port.Economy {
    private static final String GENERIC_FAILURE = "Economy transaction failed";
    private static final String UNSUPPORTED_FAILURE = "Economy operation is not supported";
    private static final String INVALID_BALANCE_FAILURE = "Economy provider returned an invalid balance";
    private static final String NULL_RESPONSE_FAILURE = "Economy provider returned no response";

    private final net.milkbowl.vault.economy.Economy vault;
    private final Executor mainThreadExecutor;
    private final Function<PlayerId, OfflinePlayer> playerResolver;

    /**
     * Creates an adapter whose Vault calls are dispatched through the supplied executor.
     *
     * @param vault registered Vault economy provider
     * @param mainThreadExecutor executor that runs work on the server thread
     * @param playerResolver resolves core player IDs to Bukkit offline players on the server thread
     */
    public VaultEconomy(
            net.milkbowl.vault.economy.Economy vault,
            Executor mainThreadExecutor,
            Function<PlayerId, OfflinePlayer> playerResolver
    ) {
        this.vault = Objects.requireNonNull(vault, "vault");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.playerResolver = Objects.requireNonNull(playerResolver, "playerResolver");
    }

    @Override
    public boolean has(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        return onMainThread(() -> {
            validateAmount(amount);
            return vault.has(resolvePlayer(playerId), amount);
        });
    }

    @Override
    public EconomyResult withdraw(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        return onMainThread(() -> {
            validateAmount(amount);
            return result(vault.withdrawPlayer(resolvePlayer(playerId), amount));
        });
    }

    @Override
    public EconomyResult deposit(PlayerId playerId, double amount) {
        Objects.requireNonNull(playerId, "playerId");
        return onMainThread(() -> {
            validateAmount(amount);
            return result(vault.depositPlayer(resolvePlayer(playerId), amount));
        });
    }

    private OfflinePlayer resolvePlayer(PlayerId playerId) {
        return Objects.requireNonNull(playerResolver.apply(playerId), "playerResolver returned null");
    }

    private void validateAmount(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("amount must be finite and greater than zero");
        }

        int fractionalDigits = vault.fractionalDigits();
        int amountScale = Math.max(0, BigDecimal.valueOf(amount).stripTrailingZeros().scale());
        if (fractionalDigits >= 0 && amountScale > fractionalDigits) {
            throw new IllegalArgumentException(
                    "amount has " + amountScale + " fractional digits; economy supports " + fractionalDigits);
        }
    }

    private static EconomyResult result(EconomyResponse response) {
        if (response == null) {
            return new EconomyResult.Failure(NULL_RESPONSE_FAILURE);
        }
        if (response.transactionSuccess()) {
            if (!Double.isFinite(response.balance)) {
                return new EconomyResult.Failure(INVALID_BALANCE_FAILURE);
            }
            return new EconomyResult.Success(response.balance);
        }

        String reason = response.errorMessage;
        if (reason == null || reason.isBlank()) {
            reason = response.type == EconomyResponse.ResponseType.NOT_IMPLEMENTED
                    ? UNSUPPORTED_FAILURE
                    : GENERIC_FAILURE;
        }
        return new EconomyResult.Failure(reason);
    }

    private <T> T onMainThread(Supplier<T> operation) {
        var completion = new CompletableFuture<T>();
        mainThreadExecutor.execute(() -> {
            try {
                completion.complete(operation.get());
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });

        try {
            return completion.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }
}
