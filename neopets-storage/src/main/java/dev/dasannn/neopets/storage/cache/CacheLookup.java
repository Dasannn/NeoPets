package dev.dasannn.neopets.storage.cache;

import java.util.Objects;
import java.util.Optional;

record CacheLookup<T>(boolean hit, Optional<T> value) {
    CacheLookup {
        Objects.requireNonNull(value, "value");
    }

    static <T> CacheLookup<T> miss() {
        return new CacheLookup<>(false, Optional.empty());
    }

    static <T> CacheLookup<T> hit(T value) {
        return hitOptional(Optional.of(value));
    }

    static <T> CacheLookup<T> hitOptional(Optional<T> value) {
        return new CacheLookup<>(true, value);
    }
}
