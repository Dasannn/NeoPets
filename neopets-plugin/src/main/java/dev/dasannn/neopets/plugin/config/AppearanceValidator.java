package dev.dasannn.neopets.plugin.config;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.List;

/** Validates opaque appearance properties for one platform-specific pet type. */
@FunctionalInterface
public interface AppearanceValidator {
    /**
     * Returns validation failure reasons, or an empty list when the appearance is supported.
     *
     * @param petType pet type owning the variant
     * @param appearance opaque appearance to validate
     * @return immutable or mutable failure descriptions; the loader copies them
     */
    List<String> validate(PetTypeId petType, VariantAppearance appearance);

    /** Returns a validator that accepts every appearance key and value. */
    static AppearanceValidator acceptingAll() {
        return (petType, appearance) -> List.of();
    }
}
