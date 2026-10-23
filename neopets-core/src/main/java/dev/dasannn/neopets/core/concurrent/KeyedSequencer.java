package dev.dasannn.neopets.core.concurrent;

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
    private final Map<K, CompletableFuture<Void>> tails = new HashMap<>();

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

        CompletableFuture<Void> predecessor;
        var completion = new CompletableFuture<Void>();
        synchronized (monitor) {
            predecessor = tails.put(key, completion);
        }

        completion.whenComplete((ignored, failure) -> removeIfIdle(key, completion));

        var ready = predecessor == null
                ? CompletableFuture.<Void>completedFuture(null)
                : predecessor;
        CompletableFuture<T> operation = ready.thenCompose(ignored -> invoke(action));
        var result = new CompletableFuture<T>();
        operation.whenComplete((value, failure) -> {
            completion.complete(null);
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static <T> CompletableFuture<T> invoke(Supplier<? extends CompletableFuture<T>> action) {
        return Objects.requireNonNull(action.get(), "action result");
    }

    private void removeIfIdle(K key, CompletableFuture<Void> completion) {
        synchronized (monitor) {
            if (tails.get(key) == completion) {
                tails.remove(key);
            }
        }
    }

    int trackedKeyCount() {
        synchronized (monitor) {
            return tails.size();
        }
    }
}
