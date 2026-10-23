package dev.dasannn.neopets.plugin.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.Supplier;

/** Records runtime shutdown actions and executes them once in strict reverse startup order. */
public final class RuntimeLifecycle implements AutoCloseable {
    private final Deque<AutoCloseable> shutdownActions = new ArrayDeque<>();
    private boolean closed;

    /** Starts one component and records it only after construction succeeds. */
    public <T extends AutoCloseable> T start(Supplier<T> starter) {
        Objects.requireNonNull(starter, "starter");
        return started(Objects.requireNonNull(starter.get(), "started component"));
    }

    /** Records a shutdown action after its component has started successfully. */
    public synchronized <T extends AutoCloseable> T started(T component) {
        Objects.requireNonNull(component, "component");
        if (closed) {
            throw new IllegalStateException("runtime lifecycle is closed");
        }
        shutdownActions.push(component);
        return component;
    }

    /** Runs all recorded actions in reverse order, preserving later failures as suppressed causes. */
    @Override
    public synchronized void close() throws Exception {
        if (closed) {
            return;
        }
        closed = true;
        Exception failure = null;
        while (!shutdownActions.isEmpty()) {
            try {
                shutdownActions.pop().close();
            } catch (Exception exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
