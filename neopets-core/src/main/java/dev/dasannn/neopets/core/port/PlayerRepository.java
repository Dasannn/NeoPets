package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous persistence operations for player skill-point balances.
 *
 * <p>Pet ownership is stored exclusively through {@link PetRepository}. The pet list on a
 * {@link PlayerProfile} is an assembled domain view and must not be persisted by this port.</p>
 */
public interface PlayerRepository {
    /**
     * Returns the stored player data, or an empty optional when the player is unknown.
     * Implementations need only populate the skill-point balance.
     */
    CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId);

    /**
     * Inserts or replaces the player's skill-point balance; the owned-pet list is never persisted.
     *
     * @return a future completed after the change is durable, or completed exceptionally on failure
     */
    CompletableFuture<Void> save(PlayerProfile profile);
}
