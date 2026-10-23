package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillTreeMenuLayoutFactoryTest {
    private static final PetId PET_ID = new PetId(UUID.nameUUIDFromBytes(
            "skill-tree-preview-polar-bear".getBytes(StandardCharsets.UTF_8)));

    @TempDir
    Path temporaryDirectory;

    private SkillTreeMenuLayoutFactory factory;
    private SkillTreeDefinition polarBear;

    @BeforeEach
    void loadShippedConfiguration() throws Exception {
        var snapshot = new ConfigurationLoader(temporaryDirectory.resolve("configuration")).load();
        factory = new SkillTreeMenuLayoutFactory(snapshot.language());
        polarBear = snapshot.skillTree(new PetTypeId("polar_bear")).orElseThrow();
    }

    @Test
    void computesEveryNodeStateAndReason() {
        TreeProgress progress = TreeProgress.empty(PET_ID, polarBear)
                .withLevels("health", 3, 1);
        AttributeDefinition health = polarBear.attributes().getFirst();
        SkillTreeMenuData normal = data(progress, 5, 1, 22, 3, 22, Optional.of(BigDecimal.valueOf(10_000)));

        assertEquals(SkillNodeState.ACTIVE,
                SkillTreeMenuLayoutFactory.nodeView(normal, health, 1).state());
        assertEquals(SkillNodeState.UNLOCKED,
                SkillTreeMenuLayoutFactory.nodeView(normal, health, 2).state());
        assertEquals(SkillNodeState.LOCKED,
                SkillTreeMenuLayoutFactory.nodeView(normal, health, 3).state());
        assertEquals(SkillNodeState.NEXT_UNLOCKABLE,
                SkillTreeMenuLayoutFactory.nodeView(normal, health, 4).state());
        assertEquals(SkillNodeReason.PREVIOUS_LEVEL,
                SkillTreeMenuLayoutFactory.nodeView(normal, health, 5).reason());

        SkillTreeMenuData noMoney = data(progress, 5, 1, 22, 3, 22, Optional.of(BigDecimal.ZERO));
        assertEquals(SkillNodeReason.INSUFFICIENT_FUNDS,
                SkillTreeMenuLayoutFactory.nodeView(noMoney, health, 4).reason());
        SkillTreeMenuData nodeLimit = data(progress, 5, 1, 22, 3, 3, Optional.of(BigDecimal.TEN));
        assertEquals(SkillNodeReason.NODE_LIMIT,
                SkillTreeMenuLayoutFactory.nodeView(nodeLimit, health, 4).reason());
        SkillTreeMenuData noSp = data(progress, 0, 1, 22, 3, 22, Optional.of(BigDecimal.TEN));
        assertEquals(SkillNodeReason.INSUFFICIENT_SKILL_POINTS,
                SkillTreeMenuLayoutFactory.nodeView(noSp, health, 2).reason());
        SkillTreeMenuData spLimit = data(progress, 5, 1, 1, 3, 22, Optional.of(BigDecimal.TEN));
        assertEquals(SkillNodeReason.SKILL_POINT_LIMIT,
                SkillTreeMenuLayoutFactory.nodeView(spLimit, health, 2).reason());
    }

    @Test
    void laysOutFourAttributesPerPageWithAlignedFillersAndControls() {
        SkillTreeMenuData data = data(referenceProgress(), 7, 12, 22, 12, 22,
                Optional.of(BigDecimal.valueOf(20_000)));
        var first = factory.build(data, 0, false);
        var second = factory.build(data, 1, false);

        assertEquals(2, first.totalPages());
        assertEquals("GLISTERING_MELON_SLICE", first.layout().items().get(10).material());
        assertEquals("LIME_STAINED_GLASS_PANE", first.layout().items().get(11).material());
        assertEquals("LIGHT_GRAY_STAINED_GLASS_PANE", first.layout().items().get(16).material());
        assertEquals("SUGAR", second.layout().items().get(10).material());
        assertEquals("NETHER_STAR", second.layout().items().get(19).material());
        assertEquals("LIGHT_GRAY_STAINED_GLASS_PANE", second.layout().items().get(28).material());
        assertTrue(first.layout().items().get(50).actionId().isPresent());
        assertTrue(first.layout().items().get(48).actionId().isEmpty());
        assertTrue(second.layout().items().get(48).actionId().isPresent());
        assertTrue(second.layout().items().get(50).actionId().isEmpty());
        assertEquals("ARROW", first.layout().items().get(49).material());
        assertEquals("BOOK", first.layout().items().get(51).material());
    }

    @Test
    void resetOnlyAppearsWhenEnabledAndRequiresConfirmation() {
        SkillTreeMenuData enabled = data(referenceProgress(), 7, 12, 22, 12, 22,
                Optional.of(BigDecimal.valueOf(20_000)));
        SkillTreeMenuData disabled = new SkillTreeMenuData(enabled.petId(), enabled.petName(), enabled.typeId(),
                enabled.iconMaterial(), enabled.baby(), enabled.availableSkillPoints(),
                enabled.assignedSkillPoints(), enabled.maxSkillPoints(), enabled.unlockedNodes(),
                enabled.maxNodes(), enabled.economyBalance(), enabled.definition(), enabled.progress(),
                false, enabled.resetCost());

        assertEquals("WATER_BUCKET", factory.build(enabled, 0, false).layout().items().get(47).material());
        assertEquals("TNT", factory.build(enabled, 0, true).layout().items().get(47).material());
        assertEquals("GRAY_STAINED_GLASS_PANE",
                factory.build(disabled, 0, false).layout().items().get(47).material());
        assertFalse(factory.build(disabled, 0, false).layout().items().get(47).actionId().isPresent());
    }

    @Test
    void writesAllRequiredSkillTreePreviewsAsJson() throws Exception {
        Path output = Path.of(System.getProperty("neopets.preview.dir"));
        SkillTreeMenuData reference = data(referenceProgress(), 7, 12, 22, 12, 22,
                Optional.of(BigDecimal.valueOf(20_000)));
        MenuLayoutJson.write(output.resolve("skill-tree-polar-bear-page-1.json"),
                factory.build(reference, 0, false).layout(), List.of(4, 10, 14, 47));
        MenuLayoutJson.write(output.resolve("skill-tree-polar-bear-page-2.json"),
                factory.build(reference, 1, false).layout(), List.of(4, 10, 19, 48));

        TreeProgress fresh = TreeProgress.empty(PET_ID, polarBear);
        MenuLayoutJson.write(output.resolve("skill-tree-fresh.json"),
                factory.build(data(fresh, 3, 0, 3, 0, 10,
                        Optional.of(BigDecimal.valueOf(20_000))), 0, false).layout(), List.of(4, 11, 20));

        TreeProgress limited = fresh.withLevels("health", 5, 3).withLevels("regeneration", 5, 0);
        MenuLayoutJson.write(output.resolve("skill-tree-rank-limit.json"),
                factory.build(data(limited, 0, 3, 3, 10, 10,
                        Optional.of(BigDecimal.valueOf(20_000))), 0, false).layout(), List.of(4, 20, 29, 38));
    }

    private TreeProgress referenceProgress() {
        return TreeProgress.empty(PET_ID, polarBear)
                .withLevels("health", 4, 4)
                .withLevels("regeneration", 2, 2)
                .withLevels("fire_resistance", 1, 1)
                .withLevels("strength", 3, 3)
                .withLevels("speed", 1, 1)
                .withLevels("claw_swipe", 1, 1);
    }

    private SkillTreeMenuData data(
            TreeProgress progress,
            int availableSp,
            int assignedSp,
            int maxSp,
            int unlockedNodes,
            int maxNodes,
            Optional<BigDecimal> money
    ) {
        return new SkillTreeMenuData(PET_ID.toString(), "Nieve", "polar_bear", "SNOWBALL",
                false, availableSp, assignedSp, maxSp, unlockedNodes, maxNodes, money,
                polarBear, progress, true, BigDecimal.valueOf(5_000));
    }
}
