package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence operations for player profiles. */
public interface PlayerRepository {
    CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId);

    CompletableFuture<Void> save(PlayerProfile profile);
}
