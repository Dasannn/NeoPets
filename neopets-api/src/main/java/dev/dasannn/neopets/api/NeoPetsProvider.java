package dev.dasannn.neopets.api;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe process-wide holder for the active {@link NeoPetsApi} implementation.
 */
public final class NeoPetsProvider {

    private static final AtomicReference<NeoPetsApi> API = new AtomicReference<>();

    private NeoPetsProvider() {
    }

    /**
     * Returns the registered NeoPets API implementation.
     *
     * @return the registered API implementation
     * @throws IllegalStateException if NeoPets has not registered an implementation
     */
    public static NeoPetsApi get() {
        NeoPetsApi api = API.get();
        if (api == null) {
            throw new IllegalStateException("NeoPets API is not registered");
        }
        return api;
    }

    /**
     * Registers the process-wide NeoPets API implementation.
     *
     * @param api the implementation to register
     * @throws NullPointerException if {@code api} is {@code null}
     * @throws IllegalStateException if an implementation is already registered
     */
    public static void register(NeoPetsApi api) {
        Objects.requireNonNull(api, "api");
        if (!API.compareAndSet(null, api)) {
            throw new IllegalStateException("NeoPets API is already registered");
        }
    }

    /**
     * Removes the currently registered NeoPets API implementation, if present.
     */
    public static void unregister() {
        API.set(null);
    }
}
