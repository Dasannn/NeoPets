package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Indicates that a saddlebag save used a stale optimistic-lock version. */
public final class SaddlebagVersionConflictException extends RuntimeException {
    private final PetId petId;
    private final long expectedVersion;

    public SaddlebagVersionConflictException(PetId petId, long expectedVersion) {
        super("stale saddlebag version " + expectedVersion + " for pet " + petId);
        this.petId = Objects.requireNonNull(petId, "petId");
        this.expectedVersion = expectedVersion;
    }

    public PetId petId() { return petId; }
    public long expectedVersion() { return expectedVersion; }
}
