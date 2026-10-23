package dev.dasannn.neopets.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeWorkTrackerTest {
    @Test
    void shutdownDrainsAGatedReadThroughItsWriteAndRejectsNewWork() {
        var tracker = new RuntimeWorkTracker();
        var read = new CompletableFuture<Integer>();
        var write = new CompletableFuture<Void>();
        var writesStarted = new AtomicInteger();
        CompletableFuture<Void> command = tracker.submit(() -> read.thenCompose(ignored -> {
            writesStarted.incrementAndGet();
            return write;
        }));

        CompletableFuture<Void> drained = tracker.stopAndDrain();
        var rejected = tracker.submit(() -> CompletableFuture.completedFuture(null));

        assertFalse(drained.isDone());
        assertInstanceOf(RejectedExecutionException.class,
                assertThrows(CompletionException.class, rejected::join).getCause());
        read.complete(1);
        assertEquals(1, writesStarted.get());
        assertFalse(drained.isDone());

        write.complete(null);
        command.join();
        drained.join();
    }

    @Test
    void runtimeLifecycleClosesStorageOnlyAfterAcceptedReadToWriteWork() throws Exception {
        var events = new CopyOnWriteArrayList<String>();
        var lifecycle = new RuntimeLifecycle();
        var tracker = new RuntimeWorkTracker();
        var read = new CompletableFuture<Void>();
        var write = new CompletableFuture<Void>();
        var shutdownStarted = new CountDownLatch(1);
        lifecycle.started((AutoCloseable) () -> events.add("storage closed"));
        tracker.submit(() -> read.thenCompose(ignored -> {
            events.add("write started");
            return write;
        }));
        lifecycle.started((AutoCloseable) () -> {
            shutdownStarted.countDown();
            tracker.stopAndDrain().join();
            events.add("work drained");
        });

        CompletableFuture<Void> closing = CompletableFuture.runAsync(() -> {
            try {
                lifecycle.close();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        });
        assertTrue(shutdownStarted.await(1, TimeUnit.SECONDS));
        assertEquals(List.of(), events);

        read.complete(null);
        assertEquals(List.of("write started"), events);
        assertFalse(closing.isDone());
        write.complete(null);
        closing.join();

        assertEquals(List.of("write started", "work drained", "storage closed"), events);
    }
}
