package dev.dasannn.neopets.plugin.config;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Holds the live immutable snapshot and atomically replaces it after valid reloads. */
public final class ConfigurationManager {
    private final ConfigurationLoader loader;
    private final AtomicReference<ConfigurationSnapshot> current;

    public ConfigurationManager(ConfigurationLoader loader) throws IOException, ConfigurationLoadException {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.current = new AtomicReference<>(loader.load());
    }

    /** Returns the currently active snapshot. */
    public ConfigurationSnapshot current() {
        return current.get();
    }

    /**
     * Loads and validates a replacement, publishing it only after the entire load succeeds.
     *
     * @return the newly active snapshot
     */
    public ConfigurationSnapshot reload() throws IOException, ConfigurationLoadException {
        ConfigurationSnapshot replacement = loader.load();
        current.set(replacement);
        return replacement;
    }
}
