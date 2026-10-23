package dev.dasannn.neopets.core.event;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** A small synchronous, type-safe bus for domain events. */
public final class DomainEventBus {
    private static final System.Logger LOGGER = System.getLogger(DomainEventBus.class.getName());

    private final ConcurrentHashMap<Class<? extends DomainEvent>, CopyOnWriteArrayList<Consumer<DomainEvent>>>
            subscribers = new ConcurrentHashMap<>();
    private final ErrorHandler errorHandler;

    /** Creates a bus that reports listener failures to the system logger. */
    public DomainEventBus() {
        this((event, exception) -> LOGGER.log(
                System.Logger.Level.ERROR,
                "Domain event listener failed while handling " + event.getClass().getSimpleName(),
                exception));
    }

    /**
     * Creates a bus that reports listener failures to the supplied handler.
     *
     * @param errorHandler handler invoked on the publishing thread after a listener fails
     */
    public DomainEventBus(ErrorHandler errorHandler) {
        this.errorHandler = Objects.requireNonNull(errorHandler, "errorHandler");
    }

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

    /**
     * Publishes an event immediately to listeners in subscription order.
     *
     * <p>A failing listener is reported to the error handler and does not prevent the remaining listeners from
     * receiving the event.</p>
     */
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        var listeners = subscribers.get(event.getClass());
        if (listeners != null) {
            for (var listener : listeners) {
                try {
                    listener.accept(event);
                } catch (RuntimeException exception) {
                    reportFailure(event, exception);
                }
            }
        }
    }

    private void reportFailure(DomainEvent event, RuntimeException exception) {
        try {
            errorHandler.onListenerFailure(event, exception);
        } catch (RuntimeException handlerException) {
            exception.addSuppressed(handlerException);
            LOGGER.log(System.Logger.Level.ERROR, "Domain event error handler failed", exception);
        }
    }

    /** Receives failures thrown by event listeners. */
    @FunctionalInterface
    public interface ErrorHandler {
        /**
         * Handles a listener failure on the publishing thread.
         *
         * @param event event being delivered
         * @param exception listener failure
         */
        void onListenerFailure(DomainEvent event, RuntimeException exception);
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
