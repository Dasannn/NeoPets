package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Paper {@link TaskScheduler} backed by the owning plugin's synchronous scheduler. */
public final class PaperTaskScheduler implements TaskScheduler {
    private final Plugin plugin;
    private final Set<PendingTask> pendingTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean accepting = true;

    /** Creates a scheduler for tasks owned by {@code plugin}. */
    public PaperTaskScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public CompletableFuture<Void> runOnMain(Runnable task) {
        Objects.requireNonNull(task, "task");
        var future = new CompletableFuture<Void>();
        if (isMainThread()) {
            runAndComplete(task, future);
            return future;
        }

        if (!accepting) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException("NeoPets main-thread scheduler is shutting down"));
        }

        var pending = new PendingTask(future);
        pendingTasks.add(pending);
        try {
            BukkitTask scheduled = plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (pendingTasks.remove(pending)) {
                    runAndComplete(task, future);
                }
            });
            pending.bind(scheduled);
            if (!accepting && pendingTasks.remove(pending)) {
                pending.cancel();
            }
        } catch (RuntimeException exception) {
            pendingTasks.remove(pending);
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
    public RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
        if (!accepting) {
            throw new RejectedExecutionException("NeoPets main-thread scheduler is shutting down");
        }
        if (initialDelayTicks < 0) {
            throw new IllegalArgumentException("initialDelayTicks must not be negative");
        }
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks must be greater than zero");
        }
        var bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        return new PaperRepeatingTask(bukkitTask);
    }

    @Override
    public boolean isMainThread() {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void quiesce() {
        accepting = false;
        for (PendingTask pending : Set.copyOf(pendingTasks)) {
            if (pendingTasks.remove(pending)) {
                pending.cancel();
            }
        }
    }

    private static void runAndComplete(Runnable task, CompletableFuture<Void> future) {
        try {
            task.run();
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private record PaperRepeatingTask(BukkitTask task) implements RepeatingTask {
        private PaperRepeatingTask {
            Objects.requireNonNull(task, "task");
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    private static final class PendingTask {
        private final CompletableFuture<Void> future;
        private BukkitTask task;
        private boolean cancelled;

        private PendingTask(CompletableFuture<Void> future) {
            this.future = future;
        }

        private synchronized void bind(BukkitTask task) {
            this.task = Objects.requireNonNull(task, "task");
            if (cancelled) {
                task.cancel();
            }
        }

        private synchronized void cancel() {
            cancelled = true;
            if (task != null) {
                task.cancel();
            }
            future.completeExceptionally(
                    new RejectedExecutionException("NeoPets main-thread task cancelled during shutdown"));
        }
    }
}
