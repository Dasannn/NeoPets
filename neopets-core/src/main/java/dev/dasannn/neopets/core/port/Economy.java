package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous, platform-neutral access to the server economy.
 *
 * <p>Every operation may be called from any thread. Implementations run the platform call on the
 * thread required by that platform and complete the returned future afterwards.</p>
 */
public interface Economy {
    /**
     * Reads the player's balance for a sufficiency check.
     *
     * @param playerId player whose balance is checked; never {@code null}
     * @param amount finite amount greater than zero
     * @return a future containing the current balance or an adapter failure; invalid amounts may
     *         complete it exceptionally with {@link IllegalArgumentException}
     */
    CompletableFuture<EconomyResult> has(PlayerId playerId, double amount);

    /**
     * Withdraws an amount from the player's account.
     *
     * @param playerId player whose balance is changed; never {@code null}
     * @param amount finite amount greater than zero
     * @return a future containing the adapter result, including the resulting balance on success;
     *         invalid amounts may complete it exceptionally with {@link IllegalArgumentException}
     */
    CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount);

    /**
     * Deposits an amount into the player's account.
     *
     * @param playerId player whose balance is changed; never {@code null}
     * @param amount finite amount greater than zero
     * @return a future containing the adapter result, including the resulting balance on success;
     *         invalid amounts may complete it exceptionally with {@link IllegalArgumentException}
     */
    CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount);
}
