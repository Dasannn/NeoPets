package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;

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
        effects.put("max_health", additive("max_health", ValueTransform.IDENTITY, true));
        effects.put("armor", additive("armor", ValueTransform.IDENTITY));
        effects.put("attack_damage", additive("attack_damage", ValueTransform.IDENTITY));
        effects.put("movement_speed", scalar("movement_speed", ValueTransform.IDENTITY));
        effects.put("flying_speed", scalar("flying_speed", ValueTransform.IDENTITY));
        effects.put("scale", scalar("scale", ValueTransform.CLAMP_SCALE_BONUS));
        effects.put("knockback_resistance",
                additive("knockback_resistance", ValueTransform.CLAMP_UNIT));
        effects.put("explosion_knockback_resistance",
                additive("explosion_knockback_resistance", ValueTransform.CLAMP_UNIT));
        effects.put("detection_radius", additive("follow_range", ValueTransform.IDENTITY));
        effects.put("safe_fall_distance",
                additive("safe_fall_distance", ValueTransform.NON_NEGATIVE));
        effects.put("fall_damage_multiplier",
                scalar("fall_damage_multiplier", ValueTransform.NEGATE_CLAMPED_UNIT));
        effects.put("fall_damage_reduction",
                scalar("fall_damage_multiplier", ValueTransform.NEGATE_CLAMPED_UNIT));
        effects.put("damage_resistance", potion("resistance", true));
        effects.put("fire_resistance", potion("fire_resistance", false));
        effects.put("regeneration", potion("regeneration", true));
        effects.put("water_breathing", potion("water_breathing", false));
        return Map.copyOf(effects);
    }

    private static AttributeEffect additive(String key, ValueTransform transform) {
        return additive(key, transform, false);
    }

    private static AttributeEffect additive(String key, ValueTransform transform, boolean maximumHealth) {
        return new AttributeEffect(
                NamespacedKey.minecraft(key), transform, AttributeModifier.Operation.ADD_NUMBER, maximumHealth);
    }

    private static AttributeEffect scalar(String key, ValueTransform transform) {
        return new AttributeEffect(
                NamespacedKey.minecraft(key), transform, AttributeModifier.Operation.ADD_SCALAR, false);
    }

    private static PotionEffect potion(String key, boolean amplifierByLevel) {
        return new PotionEffect(NamespacedKey.minecraft(key), amplifierByLevel);
    }

    /** Paper-side effect selected by a catalog identifier. */
    public sealed interface Effect permits AttributeEffect, PotionEffect {
    }

    /**
     * A bonus relative to the entity's own base attribute value.
     *
     * <p>Numeric stats use {@link AttributeModifier.Operation#ADD_NUMBER}; movement, flight, scale and fall-damage
     * reductions use {@link AttributeModifier.Operation#ADD_SCALAR}. This keeps upgrades valid for every mob base
     * value and across vanilla balance changes.</p>
     */
    public record AttributeEffect(
            NamespacedKey attributeKey,
            ValueTransform transform,
            AttributeModifier.Operation operation,
            boolean maximumHealth
    ) implements Effect {
        public AttributeEffect {
            Objects.requireNonNull(attributeKey, "attributeKey");
            Objects.requireNonNull(transform, "transform");
            Objects.requireNonNull(operation, "operation");
        }

        /** Converts a configured cumulative bonus into the amount stored in the modifier. */
        public double modifierAmount(BigDecimal configured) {
            Objects.requireNonNull(configured, "configured");
            double rawValue = configured.doubleValue();
            if (!Double.isFinite(rawValue)) {
                throw new IllegalArgumentException("configured effect value must be finite");
            }
            return transform.apply(rawValue);
        }

        /** Calculates the unclamped value produced from a base value, for validation and pure tests. */
        public double valueFromBase(double baseValue, BigDecimal configured) {
            double amount = modifierAmount(configured);
            return switch (operation) {
                case ADD_NUMBER -> baseValue + amount;
                case ADD_SCALAR -> baseValue + baseValue * amount;
                case MULTIPLY_SCALAR_1 -> baseValue * (1.0 + amount);
            };
        }
    }

    /** An infinite ambient potion effect, optionally using the active level as its amplifier. */
    public record PotionEffect(NamespacedKey typeKey, boolean amplifierByLevel) implements Effect {
        public PotionEffect {
            Objects.requireNonNull(typeKey, "typeKey");
        }
    }

    /** Converts a configured cumulative bonus into a Paper modifier amount. */
    public enum ValueTransform {
        /** Uses the configured value without changing it. */
        IDENTITY {
            @Override
            public double apply(double value) {
                return value;
            }
        },
        /** Prevents negative additive bonuses. */
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
        /** Restricts the scale bonus so the resulting base multiplier stays within x0.5–x2.0. */
        CLAMP_SCALE_BONUS {
            @Override
            public double apply(double value) {
                return Math.clamp(value, -0.5, 1.0);
            }
        },
        /** Converts a positive reduction fraction into a negative scalar bonus. */
        NEGATE_CLAMPED_UNIT {
            @Override
            public double apply(double value) {
                return -Math.clamp(value, 0.0, 1.0);
            }
        };

        /** Applies this conversion. */
        public abstract double apply(double value);
    }
}
