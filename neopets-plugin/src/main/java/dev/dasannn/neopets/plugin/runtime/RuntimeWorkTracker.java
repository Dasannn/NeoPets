package dev.dasannn.neopets.plugin.runtime;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/** Gates externally initiated runtime work and provides a complete shutdown barrier. */
public final class RuntimeWorkTracker {
    private final Set<CompletableFuture<?>> active = new HashSet<>();
    private boolean accepting = true;

    /** Starts and tracks one operation, or rejects it after shutdown begins. */
    public <T> CompletableFuture<T> submit(Supplier<? extends CompletableFuture<T>> operation) {
        Objects.requireNonNull(operation, "operation");
        CompletableFuture<T> future;
        synchronized (this) {
            if (!accepting) {
                return CompletableFuture.failedFuture(
                        new RejectedExecutionException("NeoPets runtime is shutting down"));
            }
            try {
                future = Objects.requireNonNull(operation.get(), "operation future");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
            active.add(future);
        }
        future.whenComplete((ignored, failure) -> {
            synchronized (RuntimeWorkTracker.this) {
                active.remove(future);
            }
        });
        return future;
    }

    /** Stops admission and returns a barrier for all previously accepted operations. */
    public synchronized CompletableFuture<Void> stopAndDrain() {
        accepting = false;
        CompletableFuture<?>[] settled = active.stream()
                .map(future -> future.handle((ignored, failure) -> null))
                .toArray(CompletableFuture<?>[]::new);
        return CompletableFuture.allOf(settled);
    }
}
