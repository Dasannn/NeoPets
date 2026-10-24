package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import java.util.UUID;

/** Generates collision-resistant identifiers for newly owned pet instances. */
@FunctionalInterface
public interface PetIdGenerator {
    /** Returns a new pet identifier. */
    PetId nextId();

    /** Returns a generator backed by random UUIDs. */
    static PetIdGenerator randomUuid() {
        return () -> new PetId(UUID.randomUUID());
    }
}
