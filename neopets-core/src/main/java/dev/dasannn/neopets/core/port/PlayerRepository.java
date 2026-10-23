package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence operations for player profiles. */
public interface PlayerRepository {
    /** Returns the stored profile, or an empty optional when the player is unknown. */
    CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId);

    /**
     * Inserts or replaces a profile by its player identifier.
     *
     * @return a future completed after the change is durable, or completed exceptionally on failure
     */
    CompletableFuture<Void> save(PlayerProfile profile);
}
