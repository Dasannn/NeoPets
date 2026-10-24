package dev.dasannn.neopets.core.model;

import java.util.Objects;
import java.util.UUID;

/** The unique identifier of a player, independent of a platform player type. */
public record PlayerId(UUID value) {
    public PlayerId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
