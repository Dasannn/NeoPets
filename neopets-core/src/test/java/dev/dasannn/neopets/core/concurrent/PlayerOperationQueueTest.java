package dev.dasannn.neopets.core.concurrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class PlayerOperationQueueTest {
    @Test
    void stopAndDrainWaitsForAcceptedOperationsAndRejectsLaterOnes() {
        var queue = new PlayerOperationQueue();
        var operation = new CompletableFuture<Void>();
        queue.submit(new PlayerId(UUID.randomUUID()), () -> operation);

        CompletableFuture<Void> drained = queue.stopAndDrain();
        CompletableFuture<Void> rejected = queue.submit(
                new PlayerId(UUID.randomUUID()), () -> CompletableFuture.completedFuture(null));

        assertFalse(drained.isDone());
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, rejected::join).getCause());
        operation.complete(null);
        drained.join();
    }
}
