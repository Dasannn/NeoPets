package dev.dasannn.neopets.plugin.runtime;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.platform.paper.TaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Coordinates runtime quiescence without leaving server-thread tasks ahead of a blocking drain. */
final class RuntimeShutdown implements AutoCloseable {
    private final RuntimeWorkTracker work;
    private final PlayerOperationQueue playerOperations;
    private final TaskScheduler scheduler;
    private final Supplier<CompletableFuture<Void>> despawnAll;
    private final Supplier<CompletableFuture<Void>> flushAll;
    private final Duration timeout;

    RuntimeShutdown(
            RuntimeWorkTracker work,
            PlayerOperationQueue playerOperations,
            TaskScheduler scheduler,
            Supplier<CompletableFuture<Void>> despawnAll,
            Supplier<CompletableFuture<Void>> flushAll,
            Duration timeout
    ) {
        this.work = Objects.requireNonNull(work, "work");
        this.playerOperations = Objects.requireNonNull(playerOperations, "playerOperations");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.despawnAll = Objects.requireNonNull(despawnAll, "despawnAll");
        this.flushAll = Objects.requireNonNull(flushAll, "flushAll");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public void close() throws Exception {
        var failures = new ArrayList<Throwable>();
        CompletableFuture<Void> workDrain = invoke(work::stopAndDrain, failures);
        CompletableFuture<Void> operationDrain = invoke(playerOperations::stopAndDrain, failures);

        run(scheduler::quiesce, failures);
        finishWorldCleanup(invoke(despawnAll, failures), failures);
        await(workDrain, failures);
        await(operationDrain, failures);
        await(invoke(flushAll, failures), failures);

        throwIfFailed(failures);
    }

    private CompletableFuture<Void> invoke(
            Supplier<CompletableFuture<Void>> operation,
            List<Throwable> failures
    ) {
        try {
            return Objects.requireNonNull(operation.get(), "shutdown operation future");
        } catch (Throwable failure) {
            failures.add(failure);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void await(CompletableFuture<Void> operation, List<Throwable> failures) {
        try {
            operation.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failures.add(failure);
        }
    }

    private void finishWorldCleanup(CompletableFuture<Void> operation, List<Throwable> failures) {
        if (!scheduler.isMainThread()) {
            await(operation, failures);
            return;
        }
        if (!operation.isDone()) {
            operation.cancel(false);
            failures.add(new IllegalStateException("world cleanup did not complete inline on the server thread"));
            return;
        }
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) {
                failures.add(failure);
            }
        });
    }

    private static void run(Runnable operation, List<Throwable> failures) {
        try {
            operation.run();
        } catch (Throwable failure) {
            failures.add(failure);
        }
    }

    private static void throwIfFailed(List<Throwable> failures) throws Exception {
        if (failures.isEmpty()) {
            return;
        }
        Throwable first = failures.getFirst();
        failures.stream().skip(1).forEach(first::addSuppressed);
        if (first instanceof Error error) {
            throw error;
        }
        if (first instanceof Exception exception) {
            throw exception;
        }
        throw new Exception(first);
    }
}
