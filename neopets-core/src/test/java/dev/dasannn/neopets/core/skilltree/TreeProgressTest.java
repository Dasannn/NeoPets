package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TreeProgressTest {
    private final PetId petId = new PetId(UUID.randomUUID());

    @Test
    void attributeProgressEnforcesEveryLevelInvariant() {
        assertEquals(new AttributeLevelProgress(2, 1, 3), new AttributeLevelProgress(2, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> new AttributeLevelProgress(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new AttributeLevelProgress(-1, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AttributeLevelProgress(4, 0, 3));
        assertThrows(IllegalArgumentException.class, () -> new AttributeLevelProgress(2, -1, 3));
        assertThrows(IllegalArgumentException.class, () -> new AttributeLevelProgress(2, 3, 3));
    }

    @Test
    void emptyProgressUsesEveryDefinitionAttributeAndItsMaximum() {
        var definition = definition();

        var progress = TreeProgress.empty(petId, definition);

        assertEquals(List.of("life", "claw"), progress.attributes().keySet().stream().toList());
        assertEquals(new AttributeLevelProgress(0, 0, 3), progress.attributes().get("life"));
        assertEquals(new AttributeLevelProgress(0, 0, 1), progress.attributes().get("claw"));
        assertEquals(0, progress.unlockedNodes());
        assertEquals(0, progress.activeNodes());
    }

    @Test
    void withMethodsReturnValidatedCopiesAndQueriesCountNodes() {
        var original = TreeProgress.empty(petId, definition());

        var unlocked = original.withUnlockedLevel("life", 3);
        var active = unlocked.withActiveLevel("life", 2).withLevels("claw", 1, 1);

        assertEquals(0, original.unlockedNodes());
        assertEquals(4, active.unlockedNodes());
        assertEquals(3, active.activeNodes());
        assertEquals(new AttributeLevelProgress(3, 2, 3), active.attributes().get("life"));
        assertThrows(IllegalArgumentException.class, () -> original.withActiveLevel("life", 1));
        assertThrows(IllegalArgumentException.class, () -> unlocked.withActiveLevel("life", 4));
        assertThrows(IllegalArgumentException.class, () -> original.withUnlockedLevel("life", 4));
        assertThrows(IllegalArgumentException.class, () -> original.withLevels("life", 1, 2));
        assertThrows(IllegalArgumentException.class, () -> original.withUnlockedLevel("speed", 1));
    }

    @Test
    void assignedSkillPointsUsesEveryActiveLevelsConfiguredCost() {
        var definition = new SkillTreeDefinition(new PetTypeId("polar_bear"), List.of(
                new AttributeDefinition("life", AttributeCategory.VANILLA, 3,
                        new VanillaEffectReference("max_health",
                                List.of(BigDecimal.ONE, BigDecimal.TWO, BigDecimal.valueOf(3))),
                        List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        List.of(0, 2, 5)),
                new AttributeDefinition("claw", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("claw"), List.of(BigDecimal.ZERO), List.of(3))
        ));

        var oneActiveLevel = TreeProgress.empty(petId, definition).withLevels("life", 1, 1);
        var twoActiveLevels = oneActiveLevel.withLevels("life", 2, 2);
        var allActive = twoActiveLevels.withLevels("life", 3, 3).withLevels("claw", 1, 1);

        assertEquals(1, oneActiveLevel.activeNodes());
        assertEquals(0, oneActiveLevel.assignedSkillPoints(definition));
        assertEquals(2, twoActiveLevels.activeNodes());
        assertEquals(2, twoActiveLevels.assignedSkillPoints(definition));
        assertEquals(4, allActive.activeNodes());
        assertEquals(10, allActive.assignedSkillPoints(definition));
    }

    @Test
    void assignedSkillPointsRejectsIncompatibleDefinitions() {
        var progress = TreeProgress.empty(petId, definition());
        var absentAttribute = new SkillTreeDefinition(new PetTypeId("polar_bear"), List.of());
        var changedMaximum = new SkillTreeDefinition(new PetTypeId("polar_bear"), List.of(
                new AttributeDefinition("life", AttributeCategory.VANILLA, 2,
                        new VanillaEffectReference("max_health", List.of(BigDecimal.ONE, BigDecimal.TWO)),
                        List.of(BigDecimal.ZERO, BigDecimal.ZERO)),
                new AttributeDefinition("claw", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("claw"), List.of(BigDecimal.ZERO))
        ));

        assertThrows(NullPointerException.class, () -> progress.assignedSkillPoints(null));
        assertThrows(IllegalArgumentException.class, () -> progress.assignedSkillPoints(absentAttribute));
        assertThrows(IllegalArgumentException.class, () -> progress.assignedSkillPoints(changedMaximum));
    }

    @Test
    void validatesAndDefensivelyCopiesProgressMap() {
        var source = new LinkedHashMap<String, AttributeLevelProgress>();
        source.put("life", new AttributeLevelProgress(1, 1, 3));
        var progress = new TreeProgress(petId, source);
        source.clear();

        assertEquals(1, progress.attributes().size());
        assertThrows(UnsupportedOperationException.class, () -> progress.attributes().clear());
        assertThrows(NullPointerException.class, () -> new TreeProgress(null, Map.of()));
        assertThrows(NullPointerException.class, () -> new TreeProgress(petId, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TreeProgress(petId, Map.of("Life", new AttributeLevelProgress(0, 0, 1))));
        var withNullValue = new LinkedHashMap<String, AttributeLevelProgress>();
        withNullValue.put("life", null);
        assertThrows(NullPointerException.class, () -> new TreeProgress(petId, withNullValue));
        assertThrows(NullPointerException.class, () -> TreeProgress.empty(petId, null));
    }

    @Test
    void activeAbilityUsesConfiguredEffectRatherThanAttributeName() {
        var definition = new SkillTreeDefinition(new PetTypeId("donkey"), List.of(
                new AttributeDefinition("delivery", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("courier_saddlebags"), List.of(BigDecimal.ZERO)),
                new AttributeDefinition("courier_saddlebags", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("other_ability"), List.of(BigDecimal.ZERO))));
        var progress = TreeProgress.empty(petId, definition);
        assertEquals(false, progress.hasActiveAbility(definition, "courier_saddlebags"));
        assertEquals(false, progress.withLevels("courier_saddlebags", 1, 1)
                .hasActiveAbility(definition, "courier_saddlebags"));
        assertEquals(true, progress.withLevels("delivery", 1, 1)
                .hasActiveAbility(definition, "courier_saddlebags"));
        assertEquals(false, new TreeProgress(petId, Map.of())
                .hasActiveAbility(definition, "courier_saddlebags"));
    }

    private static SkillTreeDefinition definition() {
        return new SkillTreeDefinition(new PetTypeId("polar_bear"), List.of(
                new AttributeDefinition("life", AttributeCategory.VANILLA, 3,
                        new VanillaEffectReference("max_health",
                                List.of(BigDecimal.ONE, BigDecimal.TWO, BigDecimal.valueOf(3))),
                        List.of(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                new AttributeDefinition("claw", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("claw"), List.of(BigDecimal.ZERO))
        ));
    }
}
