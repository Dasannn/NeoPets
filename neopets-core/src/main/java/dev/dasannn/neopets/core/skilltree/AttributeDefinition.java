package dev.dasannn.neopets.core.skilltree;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Defines one ordered skill-tree branch, including its effect and per-level costs.
 */
public record AttributeDefinition(
        String id,
        AttributeCategory category,
        int maxLevel,
        EffectReference effect,
        List<BigDecimal> unlockMoneyCosts,
        List<Integer> skillPointCosts
) {
    public AttributeDefinition {
        id = SkillTreeValidation.requireLowercaseKey(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(unlockMoneyCosts, "unlockMoneyCosts");
        Objects.requireNonNull(skillPointCosts, "skillPointCosts");
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least one");
        }

        unlockMoneyCosts = List.copyOf(unlockMoneyCosts);
        skillPointCosts = List.copyOf(skillPointCosts);
        requireLevelCount(unlockMoneyCosts, maxLevel, "unlockMoneyCosts");
        requireLevelCount(skillPointCosts, maxLevel, "skillPointCosts");

        for (BigDecimal cost : unlockMoneyCosts) {
            if (cost.signum() < 0) {
                throw new IllegalArgumentException("unlockMoneyCosts must not contain negative values");
            }
        }
        for (int cost : skillPointCosts) {
            if (cost < 0) {
                throw new IllegalArgumentException("skillPointCosts must not contain negative values");
            }
        }

        switch (category) {
            case VANILLA -> {
                if (!(effect instanceof VanillaEffectReference vanillaEffect)) {
                    throw new IllegalArgumentException("VANILLA attributes require a vanilla effect reference");
                }
                requireLevelCount(vanillaEffect.valuesPerLevel(), maxLevel, "vanilla effect valuesPerLevel");
            }
            case CUSTOM -> {
                if (!(effect instanceof CustomEffectReference customEffect)) {
                    throw new IllegalArgumentException("CUSTOM attributes require a custom effect reference");
                }
                customEffect.parametersPerLevel().ifPresent(parameters ->
                        requireLevelCount(parameters, maxLevel, "custom effect parametersPerLevel"));
            }
        }
    }

    /** Creates a definition whose every level costs the default of one skill point. */
    public AttributeDefinition(
            String id,
            AttributeCategory category,
            int maxLevel,
            EffectReference effect,
            List<BigDecimal> unlockMoneyCosts
    ) {
        this(id, category, maxLevel, effect, unlockMoneyCosts,
                Collections.nCopies(Math.max(maxLevel, 0), 1));
    }

    private static void requireLevelCount(List<?> values, int maxLevel, String fieldName) {
        if (values.size() != maxLevel) {
            throw new IllegalArgumentException(fieldName + " size must equal maxLevel");
        }
    }
}
