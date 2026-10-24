package dev.dasannn.neopets.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.platform.paper.TaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class RuntimeShutdownTest {
    @Test
    void compositionRootCancelsQueuedMainWorkAndRunsEveryLaterCleanupStep() {
        var events = new ArrayList<String>();
        var lifecycle = new RuntimeLifecycle();
        var work = new RuntimeWorkTracker();
        var playerOperations = new PlayerOperationQueue();
        var scheduler = new QueuedMainThreadScheduler(events);
        lifecycle.started((AutoCloseable) () -> events.add("storage closed"));

        var queuedWorldWork = work.submit(() -> scheduler.runOnMain(() -> events.add("queued task ran")));
        lifecycle.started(new RuntimeShutdown(
                work,
                playerOperations,
                scheduler,
                () -> {
                    events.add("despawn attempted");
                    return CompletableFuture.failedFuture(new IllegalStateException("despawn failed"));
                },
                () -> {
                    events.add("cache flushed");
                    return CompletableFuture.completedFuture(null);
                },
                Duration.ofSeconds(1)));
        scheduler.enterMainThread();

        assertThrows(Exception.class, lifecycle::close);

        assertTrue(queuedWorldWork.isCompletedExceptionally());
        assertFalse(events.contains("queued task ran"));
        assertEquals(List.of(
                "scheduler quiesced", "despawn attempted", "cache flushed", "storage closed"), events);
    }

    private static final class QueuedMainThreadScheduler implements TaskScheduler {
        private final List<String> events;
        private final List<CompletableFuture<Void>> queued = new ArrayList<>();
        private boolean mainThread;

        private QueuedMainThreadScheduler(List<String> events) {
            this.events = events;
        }

        @Override
        public CompletableFuture<Void> runOnMain(Runnable task) {
            var future = new CompletableFuture<Void>();
            queued.add(future);
            return future;
        }

        @Override
        public RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMainThread() {
            return mainThread;
        }

        @Override
        public void quiesce() {
            events.add("scheduler quiesced");
            queued.forEach(future -> future.completeExceptionally(
                    new RejectedExecutionException("cancelled for shutdown")));
            queued.clear();
        }

        private void enterMainThread() {
            mainThread = true;
        }
    }
}
