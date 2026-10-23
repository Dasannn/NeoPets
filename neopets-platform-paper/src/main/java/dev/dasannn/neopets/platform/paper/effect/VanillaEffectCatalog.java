package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.NamespacedKey;

/** Maps configured vanilla effect identifiers to their Paper 26.3 representation. */
public final class VanillaEffectCatalog {
    private static final Map<String, Effect> EFFECTS = effects();

    /** Returns every effect identifier accepted by skill-tree configuration validation. */
    public static Set<String> knownEffectIds() {
        return EFFECTS.keySet();
    }

    /** Finds the Paper representation of a configured vanilla effect identifier. */
    public Optional<Effect> find(String effectId) {
        Objects.requireNonNull(effectId, "effectId");
        return Optional.ofNullable(EFFECTS.get(effectId));
    }

    /**
     * Selects the configured value for a one-based active level.
     * Level zero represents an inactive attribute and therefore has no value.
     */
    public Optional<BigDecimal> valueAtLevel(VanillaEffectReference reference, int activeLevel) {
        Objects.requireNonNull(reference, "reference");
        if (activeLevel < 0 || activeLevel > reference.valuesPerLevel().size()) {
            throw new IllegalArgumentException("activeLevel is outside the configured value range");
        }
        return activeLevel == 0
                ? Optional.empty()
                : Optional.of(reference.valuesPerLevel().get(activeLevel - 1));
    }

    private static Map<String, Effect> effects() {
        var effects = new LinkedHashMap<String, Effect>();
        effects.put("max_health", attribute("max_health", ValueTransform.IDENTITY, true));
        effects.put("armor", attribute("armor", ValueTransform.IDENTITY));
        effects.put("attack_damage", attribute("attack_damage", ValueTransform.IDENTITY));
        effects.put("movement_speed", attribute("movement_speed", ValueTransform.IDENTITY));
        effects.put("flying_speed", attribute("flying_speed", ValueTransform.IDENTITY));
        effects.put("scale", attribute("scale", ValueTransform.CLAMP_SCALE));
        effects.put("knockback_resistance",
                attribute("knockback_resistance", ValueTransform.CLAMP_UNIT));
        effects.put("explosion_knockback_resistance",
                attribute("explosion_knockback_resistance", ValueTransform.CLAMP_UNIT));
        effects.put("detection_radius", attribute("follow_range", ValueTransform.IDENTITY));
        effects.put("safe_fall_distance",
                attribute("safe_fall_distance", ValueTransform.NON_NEGATIVE));
        effects.put("fall_damage_multiplier",
                attribute("fall_damage_multiplier", ValueTransform.CLAMP_UNIT));
        effects.put("fall_damage_reduction",
                attribute("fall_damage_multiplier", ValueTransform.REDUCTION_TO_MULTIPLIER));
        effects.put("damage_resistance", potion("resistance", true));
        effects.put("fire_resistance", potion("fire_resistance", false));
        effects.put("regeneration", potion("regeneration", true));
        effects.put("water_breathing", potion("water_breathing", false));
        return Map.copyOf(effects);
    }

    private static AttributeEffect attribute(String key, ValueTransform transform) {
        return attribute(key, transform, false);
    }

    private static AttributeEffect attribute(String key, ValueTransform transform, boolean maximumHealth) {
        return new AttributeEffect(NamespacedKey.minecraft(key), transform, maximumHealth);
    }

    private static PotionEffect potion(String key, boolean amplifierByLevel) {
        return new PotionEffect(NamespacedKey.minecraft(key), amplifierByLevel);
    }

    /** Paper-side effect selected by a catalog identifier. */
    public sealed interface Effect permits AttributeEffect, PotionEffect {
    }

    /** An effect implemented as an additive modifier that reaches an absolute configured value. */
    public record AttributeEffect(
            NamespacedKey attributeKey,
            ValueTransform transform,
            boolean maximumHealth
    ) implements Effect {
        public AttributeEffect {
            Objects.requireNonNull(attributeKey, "attributeKey");
            Objects.requireNonNull(transform, "transform");
        }
    }

    /** An infinite ambient potion effect, optionally using the active level as its amplifier. */
    public record PotionEffect(NamespacedKey typeKey, boolean amplifierByLevel) implements Effect {
        public PotionEffect {
            Objects.requireNonNull(typeKey, "typeKey");
        }
    }

    /** Converts a configured decimal into the absolute value expected by a Paper attribute. */
    public enum ValueTransform {
        /** Uses the configured value without changing it. */
        IDENTITY {
            @Override
            public double apply(double value) {
                return value;
            }
        },
        /** Prevents negative absolute attribute values. */
        NON_NEGATIVE {
            @Override
            public double apply(double value) {
                return Math.max(0.0, value);
            }
        },
        /** Restricts a value to the vanilla unit interval. */
        CLAMP_UNIT {
            @Override
            public double apply(double value) {
                return Math.clamp(value, 0.0, 1.0);
            }
        },
        /** Restricts entity scale to the supported NeoPets range. */
        CLAMP_SCALE {
            @Override
            public double apply(double value) {
                return Math.clamp(value, 0.5, 2.0);
            }
        },
        /** Converts a reduction fraction into the remaining fall-damage multiplier. */
        REDUCTION_TO_MULTIPLIER {
            @Override
            public double apply(double value) {
                return 1.0 - Math.clamp(value, 0.0, 1.0);
            }
        };

        /** Applies this conversion. */
        public abstract double apply(double value);
    }
}
