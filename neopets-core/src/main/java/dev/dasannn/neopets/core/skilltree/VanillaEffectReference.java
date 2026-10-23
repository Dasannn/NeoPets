package dev.dasannn.neopets.core.skilltree;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** A vanilla effect catalog entry and the value applied at each attribute level. */
public record VanillaEffectReference(String catalogEffectId, List<BigDecimal> valuesPerLevel)
        implements EffectReference {
    public VanillaEffectReference {
        catalogEffectId = SkillTreeValidation.requireLowercaseKey(catalogEffectId, "catalogEffectId");
        Objects.requireNonNull(valuesPerLevel, "valuesPerLevel");
        valuesPerLevel = List.copyOf(valuesPerLevel);
    }
}
