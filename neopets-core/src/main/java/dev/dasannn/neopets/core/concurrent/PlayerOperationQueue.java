package dev.dasannn.neopets.core.concurrent;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Shared sequence for operations that mutate one player's state across core services.
 *
 * <p>A single instance must be injected into every service participating in a player-level
 * transaction so that mutations spanning multiple repositories cannot interleave.</p>
 */
public final class PlayerOperationQueue {
    private final KeyedSequencer<PlayerId> sequencer = new KeyedSequencer<>();

    /**
     * Runs an asynchronous operation after earlier operations for the player have completed.
     *
     * <p>An operation already executing in this queue must use unsequenced collaborators rather
     * than submit another operation for the same player and wait for it.</p>
     */
    public <T> CompletableFuture<T> submit(
            PlayerId playerId, Supplier<? extends CompletableFuture<T>> operation
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operation, "operation");
        return sequencer.submit(playerId, operation);
    }
}
