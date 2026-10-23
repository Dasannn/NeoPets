package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;

/** Platform-neutral access to the server economy. */
public interface Economy {
    /**
     * Returns whether the player has at least the requested amount.
     *
     * @param playerId player whose balance is checked; never {@code null}
     * @param amount finite, non-negative amount
     * @return whether the balance covers the amount
     * @throws IllegalArgumentException if {@code amount} is negative or non-finite
     */
    boolean has(PlayerId playerId, double amount);

    /**
     * Withdraws an amount from the player's account.
     *
     * @param playerId player whose balance is changed; never {@code null}
     * @param amount finite, non-negative amount
     * @return the adapter result, including the resulting balance on success
     * @throws IllegalArgumentException if {@code amount} is negative or non-finite
     */
    EconomyResult withdraw(PlayerId playerId, double amount);

    /**
     * Deposits an amount into the player's account.
     *
     * @param playerId player whose balance is changed; never {@code null}
     * @param amount finite, non-negative amount
     * @return the adapter result, including the resulting balance on success
     * @throws IllegalArgumentException if {@code amount} is negative or non-finite
     */
    EconomyResult deposit(PlayerId playerId, double amount);
}
