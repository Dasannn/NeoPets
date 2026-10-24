package dev.dasannn.neopets.plugin.command;

import java.util.Map;
import java.util.Objects;

/** A localized message key and its untrusted placeholder values. */
public record MessageSpec(String key, Map<String, String> placeholders) {
    public MessageSpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        placeholders = Map.copyOf(placeholders);
    }

    /** Creates a message without placeholders. */
    public static MessageSpec of(String key) {
        return new MessageSpec(key, Map.of());
    }
}
