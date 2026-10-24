package dev.dasannn.neopets.core.concurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Sequences asynchronous actions for equal keys while allowing different keys to progress independently.
 *
 * @param <K> key type used to select a sequence
 */
public final class KeyedSequencer<K> {
    private final Object monitor = new Object();
    private final Map<K, QueueState> queues = new HashMap<>();

    /**
     * Runs an action after all previously submitted actions for the same key have finished.
     * A failed action does not prevent later actions from running.
     *
     * @param key sequence key
     * @param action action that supplies its asynchronous completion
     * @param <T> result type
     * @return a future containing the action result
     */
    public <T> CompletableFuture<T> submit(K key, Supplier<? extends CompletableFuture<T>> action) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");

        var entry = new QueueEntry<>(action);
        QueueState queue;
        boolean startDrain = false;
        synchronized (monitor) {
            queue = queues.computeIfAbsent(key, ignored -> new QueueState());
            queue.entries.addLast(entry);
            if (!queue.draining) {
                queue.draining = true;
                startDrain = true;
            }
        }

        if (startDrain) {
            drain(key, queue);
        }
        return entry.result;
    }

    /**
     * Returns a future that completes after every action submitted before this call has finished.
     * Actions submitted after this method takes its snapshot are not awaited. If any action in the
     * snapshot fails, the returned future also fails after all snapshot actions finish.
     *
     * @return completion barrier for all currently tracked key sequences
     */
    public CompletableFuture<Void> awaitAll() {
        CompletableFuture<?>[] actions;
        synchronized (monitor) {
            var snapshot = new ArrayList<CompletableFuture<?>>();
            for (QueueState queue : queues.values()) {
                if (queue.active != null) {
                    snapshot.add(queue.active.settled);
                }
                queue.entries.stream().map(entry -> entry.settled).forEach(snapshot::add);
            }
            actions = snapshot.toArray(CompletableFuture<?>[]::new);
        }
        return CompletableFuture.allOf(actions);
    }

    private void drain(K key, QueueState queue) {
        while (true) {
            QueueEntry<?> entry;
            synchronized (monitor) {
                entry = queue.entries.pollFirst();
                if (entry == null) {
                    queue.active = null;
                    queue.draining = false;
                    queues.remove(key, queue);
                    return;
                }
                queue.active = entry;
            }

            if (!runEntry(key, queue, entry)) {
                return;
            }
        }
    }

    private <T> boolean runEntry(K key, QueueState queue, QueueEntry<T> entry) {
        var operation = entry.invoke();
        if (operation.isDone()) {
            entry.completeFrom(operation);
            return true;
        }

        operation.whenComplete((value, failure) -> {
            entry.complete(value, failure);
            drain(key, queue);
        });
        return false;
    }

    int trackedKeyCount() {
        synchronized (monitor) {
            return queues.size();
        }
    }

    private static final class QueueState {
        private final ArrayDeque<QueueEntry<?>> entries = new ArrayDeque<>();
        private QueueEntry<?> active;
        private boolean draining;
    }

    private static final class QueueEntry<T> {
        private final Supplier<? extends CompletableFuture<T>> action;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final CompletableFuture<Void> settled = new CompletableFuture<>();

        private QueueEntry(Supplier<? extends CompletableFuture<T>> action) {
            this.action = action;
        }

        private CompletableFuture<T> invoke() {
            try {
                return Objects.requireNonNull(action.get(), "action result");
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        private void completeFrom(CompletableFuture<T> operation) {
            operation.whenComplete(this::complete);
        }

        private void complete(T value, Throwable failure) {
            if (failure == null) {
                result.complete(value);
                settled.complete(null);
            } else {
                result.completeExceptionally(failure);
                settled.completeExceptionally(failure);
            }
        }
    }
}
