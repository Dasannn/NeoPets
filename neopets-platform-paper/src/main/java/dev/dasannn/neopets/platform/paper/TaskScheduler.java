package dev.dasannn.neopets.platform.paper;

import java.util.concurrent.CompletableFuture;

/**
 * Isolates server-thread scheduling from pet behavior code.
 *
 * <p>The abstraction deliberately models only the scheduling operations used by this module so a region-aware
 * implementation can replace the Paper scheduler later.</p>
 */
public interface TaskScheduler {
    /** Runs work on the server thread and completes after the work has finished. */
    CompletableFuture<Void> runOnMain(Runnable task);

    /** Schedules one synchronous repeating task. */
    RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks);

    /** Returns whether the caller is currently on the server thread. */
    boolean isMainThread();

    /** A cancellable repeating task without exposing Bukkit scheduler types. */
    interface RepeatingTask {
        /** Cancels future executions. */
        void cancel();

        /** Returns whether this task has been cancelled. */
        boolean isCancelled();
    }
}
