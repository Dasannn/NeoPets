package dev.dasannn.neopets.platform.paper.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.bukkit.attribute.AttributeModifier;
import org.junit.jupiter.api.Test;

class VanillaEffectCatalogTest {
    private final VanillaEffectCatalog catalog = new VanillaEffectCatalog();

    @Test
    void selectsConfiguredValuesByOneBasedLevelAndTreatsZeroAsInactive() {
        var reference = new VanillaEffectReference(
                "movement_speed", List.of(decimal("0.32"), decimal("0.36"), decimal("0.4")));

        assertTrue(catalog.valueAtLevel(reference, 0).isEmpty());
        assertEquals(decimal("0.32"), catalog.valueAtLevel(reference, 1).orElseThrow());
        assertEquals(decimal("0.4"), catalog.valueAtLevel(reference, 3).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> catalog.valueAtLevel(reference, -1));
        assertThrows(IllegalArgumentException.class, () -> catalog.valueAtLevel(reference, 4));
    }

    @Test
    void mapsShippedDetectionRangeAndVersionSpecificAttributes() {
        var detection = (VanillaEffectCatalog.AttributeEffect) catalog.find("detection_radius").orElseThrow();
        var explosion = (VanillaEffectCatalog.AttributeEffect) catalog
                .find("explosion_knockback_resistance").orElseThrow();

        assertEquals("minecraft:follow_range", detection.attributeKey().toString());
        assertEquals("minecraft:explosion_knockback_resistance", explosion.attributeKey().toString());
    }

    @Test
    void exposesEverySupportedCatalogIdentifierForConfigurationValidation() {
        assertTrue(VanillaEffectCatalog.knownEffectIds().containsAll(Set.of(
                "max_health", "armor", "attack_damage", "movement_speed", "flying_speed", "scale",
                "knockback_resistance", "explosion_knockback_resistance", "detection_radius",
                "damage_resistance", "fire_resistance", "regeneration", "safe_fall_distance",
                "fall_damage_multiplier", "fall_damage_reduction", "water_breathing")));
    }

    @Test
    void constrainsScaleAndReductionValues() {
        assertEquals(-0.5, VanillaEffectCatalog.ValueTransform.CLAMP_SCALE_BONUS.apply(-1.0));
        assertEquals(1.0, VanillaEffectCatalog.ValueTransform.CLAMP_SCALE_BONUS.apply(3.0));
        assertEquals(-0.25, VanillaEffectCatalog.ValueTransform.NEGATE_CLAMPED_UNIT.apply(0.25));
    }

    @Test
    void declaresAdditiveAndPercentageBonusOperations() {
        var health = (VanillaEffectCatalog.AttributeEffect) catalog.find("max_health").orElseThrow();
        var movement = (VanillaEffectCatalog.AttributeEffect) catalog.find("movement_speed").orElseThrow();
        var scale = (VanillaEffectCatalog.AttributeEffect) catalog.find("scale").orElseThrow();

        assertEquals(AttributeModifier.Operation.ADD_NUMBER, health.operation());
        assertEquals(44.0, health.valueFromBase(40.0, decimal("4")));
        assertEquals(AttributeModifier.Operation.ADD_SCALAR, movement.operation());
        assertEquals(0.33, movement.valueFromBase(0.3, decimal("0.1")), 0.000_001);
        assertEquals(1.5, scale.valueFromBase(1.0, decimal("0.5")));
    }

    @Test
    void modifierKeysAreStableAndAttributeSpecific() {
        assertEquals("neopets:skill/health", AttributeEffectAdapter.modifierKey("health").toString());
        assertEquals(AttributeEffectAdapter.modifierKey("health"),
                AttributeEffectAdapter.modifierKey("health"));
        assertEquals("neopets:skill/armor", AttributeEffectAdapter.modifierKey("armor").toString());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
