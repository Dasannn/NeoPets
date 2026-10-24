package dev.dasannn.neopets.core.concurrent;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Shared sequence for operations that mutate one player's state across core services.
 *
 * <p>A single instance must be injected into every service participating in a player-level
 * transaction so that mutations spanning multiple repositories cannot interleave.</p>
 */
public final class PlayerOperationQueue {
    private final KeyedSequencer<PlayerId> sequencer = new KeyedSequencer<>();
    private boolean accepting = true;

    /**
     * Runs an asynchronous operation after earlier operations for the player have completed.
     *
     * <p>An operation already executing in this queue must use unsequenced collaborators rather
     * than submit another operation for the same player and wait for it.</p>
     */
    public synchronized <T> CompletableFuture<T> submit(
            PlayerId playerId, Supplier<? extends CompletableFuture<T>> operation
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operation, "operation");
        if (!accepting) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("player operation queue is shutting down"));
        }
        return sequencer.submit(playerId, operation);
    }

    /** Stops accepting new operations and completes after every already accepted operation settles. */
    public synchronized CompletableFuture<Void> stopAndDrain() {
        accepting = false;
        return sequencer.awaitAll().handle((ignored, failure) -> null);
    }
}
