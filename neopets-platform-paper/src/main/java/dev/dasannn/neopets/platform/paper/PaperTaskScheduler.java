package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Paper {@link TaskScheduler} backed by the owning plugin's synchronous scheduler. */
public final class PaperTaskScheduler implements TaskScheduler {
    private final Plugin plugin;

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

        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> runAndComplete(task, future));
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
    public RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
        Objects.requireNonNull(task, "task");
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
}
