package dev.dasannn.neopets.core.model;

import java.util.Objects;

/** A configured, weighted visual variant of a pet type. */
public record PetVariant(
        VariantId id,
        PetTypeId type,
        VariantAppearance appearance,
        boolean baby,
        double weight,
        String displayNameKey
) {
    public PetVariant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw new IllegalArgumentException("weight must be finite and greater than zero");
        }
        if (displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey must not be blank");
        }
    }
}
