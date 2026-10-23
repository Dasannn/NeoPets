package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantId;
import java.util.Objects;
import java.util.UUID;

/** Minimal cached domain data needed to materialize a pet entity. */
public record PetSpawnData(UUID ownerId, VariantId variantId) {
    /** Validates spawn lookup data. */
    public PetSpawnData {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(variantId, "variantId");
    }
}
