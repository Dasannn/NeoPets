package dev.dasannn.neopets.plugin.config;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable lookup of configured MiniMessage source strings. */
public record LanguageCatalog(Map<String, String> messages) {
    public LanguageCatalog {
        Objects.requireNonNull(messages, "messages");
        messages = Map.copyOf(messages);
    }

    /** Finds a message by its dotted YAML path. */
    public Optional<String> find(String key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(messages.get(key));
    }
}
