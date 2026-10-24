package dev.dasannn.neopets.plugin.integration;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Presence gate that keeps PlaceholderAPI-dependent classes untouched when the plugin is absent. */
public final class PlaceholderApiRegistration {
    private PlaceholderApiRegistration() {
    }

    /** Runs the isolated registrar only when PlaceholderAPI was detected as enabled. */
    public static Optional<AutoCloseable> registerIfPresent(
            boolean placeholderApiPresent,
            Supplier<? extends AutoCloseable> registrar
    ) {
        Objects.requireNonNull(registrar, "registrar");
        if (!placeholderApiPresent) {
            return Optional.empty();
        }
        return Optional.of(Objects.requireNonNull(registrar.get(), "registrar result"));
    }
}
