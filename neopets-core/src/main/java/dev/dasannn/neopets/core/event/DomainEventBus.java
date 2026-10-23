package dev.dasannn.neopets.core.event;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A small synchronous, type-safe bus for domain events. */
public final class DomainEventBus {
    private final ConcurrentHashMap<Class<? extends DomainEvent>, CopyOnWriteArrayList<Consumer<DomainEvent>>>
            subscribers = new ConcurrentHashMap<>();

    /**
     * Registers a listener for the exact event type and returns a handle that removes it.
     *
     * @param eventType exact event class to receive
     * @param listener listener invoked on the publishing thread
     * @param <E> event type
     * @return an idempotent subscription handle
     */
    public <E extends DomainEvent> Subscription subscribe(Class<E> eventType, Consumer<? super E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");

        Consumer<DomainEvent> erasedListener = event -> listener.accept(eventType.cast(event));
        var listeners = subscribers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>());
        listeners.add(erasedListener);
        return () -> listeners.remove(erasedListener);
    }

    /** Publishes an event immediately to listeners in subscription order. */
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        var listeners = subscribers.get(event.getClass());
        if (listeners != null) {
            listeners.forEach(listener -> listener.accept(event));
        }
    }

    /** A removable event subscription. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        /** Removes this subscription; repeated calls have no effect. */
        void unsubscribe();

        @Override
        default void close() {
            unsubscribe();
        }
    }
}
