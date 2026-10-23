package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttributeDefinitionTest {
    private static final VanillaEffectReference VANILLA_EFFECT =
            new VanillaEffectReference("max_health", decimals("1", "2"));

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Vida", "max-health", "_health", "health_", "max__health"})
    void rejectsInvalidAttributeIds(String id) {
        assertThrows(IllegalArgumentException.class,
                () -> attribute(id, AttributeCategory.VANILLA, VANILLA_EFFECT));
    }

    @Test
    void requiresNonNullFieldsAndPositiveMaxLevel() {
        assertThrows(NullPointerException.class,
                () -> new AttributeDefinition(null, AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        decimals("0", "1"), List.of(1, 1)));
        assertThrows(NullPointerException.class,
                () -> new AttributeDefinition("health", null, 2, VANILLA_EFFECT,
                        decimals("0", "1"), List.of(1, 1)));
        assertThrows(NullPointerException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, null,
                        decimals("0", "1"), List.of(1, 1)));
        assertThrows(NullPointerException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        null, List.of(1, 1)));
        assertThrows(NullPointerException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        decimals("0", "1"), null));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 0,
                        new VanillaEffectReference("max_health", List.of()), List.of(), List.of()));
    }

    @Test
    void requiresAllPerLevelListsToMatchMaxLevel() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        List.of(BigDecimal.ZERO), List.of(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        decimals("0", "1"), List.of(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 3, VANILLA_EFFECT,
                        decimals("0", "1", "2"), List.of(1, 1, 1)));

        var customWithWrongCount = new CustomEffectReference(
                "claw", List.of(Map.of("radius", "3")));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("claw", AttributeCategory.CUSTOM, 2, customWithWrongCount,
                        decimals("0", "1"), List.of(1, 1)));
    }

    @Test
    void allowsCustomEffectParametersToBeOmitted() {
        var definition = new AttributeDefinition("claw", AttributeCategory.CUSTOM, 1,
                new CustomEffectReference("claw"), List.of(BigDecimal.TEN));

        assertEquals(Optional.empty(), ((CustomEffectReference) definition.effect()).parametersPerLevel());
        assertEquals(List.of(1), definition.skillPointCosts());
    }

    @Test
    void rejectsNegativeCostsAndMismatchedEffectCategories() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        decimals("0", "-1"), List.of(1, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new AttributeDefinition("health", AttributeCategory.VANILLA, 2, VANILLA_EFFECT,
                        decimals("0", "1"), List.of(1, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> attribute("health", AttributeCategory.CUSTOM, VANILLA_EFFECT));
        assertThrows(IllegalArgumentException.class,
                () -> attribute("claw", AttributeCategory.VANILLA, new CustomEffectReference("claw")));
    }

    @Test
    void defensivelyCopiesDefinitionAndEffectLists() {
        var values = new ArrayList<>(decimals("1", "2"));
        var moneyCosts = new ArrayList<>(decimals("10", "20"));
        var skillPointCosts = new ArrayList<>(List.of(0, 2));
        var definition = new AttributeDefinition("health", AttributeCategory.VANILLA, 2,
                new VanillaEffectReference("max_health", values), moneyCosts, skillPointCosts);
        values.clear();
        moneyCosts.clear();
        skillPointCosts.clear();

        assertEquals(decimals("1", "2"),
                ((VanillaEffectReference) definition.effect()).valuesPerLevel());
        assertEquals(decimals("10", "20"), definition.unlockMoneyCosts());
        assertEquals(List.of(0, 2), definition.skillPointCosts());
        assertThrows(UnsupportedOperationException.class,
                () -> definition.unlockMoneyCosts().add(BigDecimal.ONE));
    }

    @Test
    void customEffectDefensivelyCopiesNestedParameters() {
        var levelParameters = new LinkedHashMap<>(Map.of("radius", "3"));
        var allParameters = new ArrayList<>(List.of(levelParameters));
        var effect = new CustomEffectReference("claw", allParameters);
        levelParameters.put("radius", "9");
        allParameters.clear();

        assertEquals(List.of(Map.of("radius", "3")), effect.parametersPerLevel().orElseThrow());
        assertThrows(UnsupportedOperationException.class,
                () -> effect.parametersPerLevel().orElseThrow().getFirst().put("radius", "5"));
    }

    @Test
    void effectReferencesValidateIdsAndNullPayloads() {
        assertThrows(NullPointerException.class, () -> new VanillaEffectReference(null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new VanillaEffectReference("MaxHealth", List.of()));
        assertThrows(NullPointerException.class, () -> new VanillaEffectReference("health", null));
        assertThrows(NullPointerException.class, () -> new CustomEffectReference(null));
        assertThrows(IllegalArgumentException.class, () -> new CustomEffectReference("Claw"));
        assertThrows(NullPointerException.class, () -> new CustomEffectReference("claw", (Optional<List<Map<String, String>>>) null));
        assertThrows(NullPointerException.class,
                () -> new CustomEffectReference("claw", Collections.singletonList(null)));
    }

    private static AttributeDefinition attribute(
            String id,
            AttributeCategory category,
            EffectReference effect
    ) {
        return new AttributeDefinition(id, category, 2, effect, decimals("0", "1"), List.of(1, 1));
    }

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
