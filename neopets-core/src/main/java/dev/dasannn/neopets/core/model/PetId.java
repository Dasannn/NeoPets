package dev.dasannn.neopets.core.model;

import java.util.Objects;
import java.util.UUID;

/** The unique identifier of one owned pet instance. */
public record PetId(UUID value) {
    public PetId {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
