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
    /** Finds the stored player data; implementations need only populate the skill-point balance. */
    CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId);

    /** Saves only the profile's skill-point balance, never its owned-pet list. */
    CompletableFuture<Void> save(PlayerProfile profile);
}
