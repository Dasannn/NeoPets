package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillTreeDefinitionTest {
    @Test
    void buildsOrderedTwentyTwoNodePolarBearTreeFromSpec() {
        var tree = new SkillTreeDefinition(new PetTypeId("polar_bear"), List.of(
                vanilla("life", 5, "max_health"),
                vanilla("regeneration", 5, "regeneration"),
                vanilla("fire", 1, "fire_resistance"),
                vanilla("strength", 5, "attack_damage"),
                vanilla("speed", 5, "movement_speed"),
                custom("claw", "claw")
        ));

        assertEquals(List.of("life", "regeneration", "fire", "strength", "speed", "claw"),
                tree.attributes().stream().map(AttributeDefinition::id).toList());
        assertEquals(22, tree.totalNodes());
    }

    @Test
    void requiresFieldsAndUniqueAttributeIds() {
        var life = vanilla("life", 5, "max_health");

        assertThrows(NullPointerException.class, () -> new SkillTreeDefinition(null, List.of()));
        assertThrows(NullPointerException.class,
                () -> new SkillTreeDefinition(new PetTypeId("cat"), null));
        assertThrows(NullPointerException.class,
                () -> new SkillTreeDefinition(new PetTypeId("cat"), Collections.singletonList(null)));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillTreeDefinition(new PetTypeId("cat"), List.of(life, life)));
    }

    @Test
    void defensivelyCopiesAttributes() {
        var source = new ArrayList<>(List.of(vanilla("life", 1, "max_health")));
        var tree = new SkillTreeDefinition(new PetTypeId("cat"), source);
        source.clear();

        assertEquals(1, tree.attributes().size());
        assertThrows(UnsupportedOperationException.class, () -> tree.attributes().clear());
    }

    private static AttributeDefinition vanilla(String id, int levels, String effectId) {
        return new AttributeDefinition(id, AttributeCategory.VANILLA, levels,
                new VanillaEffectReference(effectId, repeatedDecimals(levels, "1")),
                repeatedDecimals(levels, "100"));
    }

    private static AttributeDefinition custom(String id, String abilityId) {
        return new AttributeDefinition(id, AttributeCategory.CUSTOM, 1,
                new CustomEffectReference(abilityId), List.of(BigDecimal.valueOf(1_000)));
    }

    private static List<BigDecimal> repeatedDecimals(int count, String value) {
        return java.util.Collections.nCopies(count, new BigDecimal(value));
    }
}
