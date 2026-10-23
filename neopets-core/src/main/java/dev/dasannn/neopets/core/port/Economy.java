package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;

/** Platform-neutral access to the server economy. */
public interface Economy {
    boolean has(PlayerId playerId, double amount);

    EconomyResult withdraw(PlayerId playerId, double amount);

    EconomyResult deposit(PlayerId playerId, double amount);
}
