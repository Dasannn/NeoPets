package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TreasureTargetSelectorTest {
    @Test
    void selectsOnlyUnopenedLootablesAndConfiguredOres() {
        var candidates = List.of(
                candidate("unopened chest", 1, true, true, Material.CHEST),
                candidate("generated chest", 2, true, false, Material.CHEST),
                candidate("ordinary barrel", 3, false, false, Material.BARREL),
                candidate("diamond", 4, false, false, Material.DIAMOND_ORE),
                candidate("iron not configured", 5, false, false, Material.IRON_ORE));

        var selection = TreasureTargetSelector.select(
                candidates, 0, 0, 0, 8, 0, Set.of(Material.DIAMOND_ORE), 20, 20);

        assertEquals(List.of("unopened chest", "diamond"), selection.targets());
        assertEquals(5, selection.scannedBlocks());
    }

    @Test
    void scentBonusExtendsTheConfiguredBaseRadius() {
        var candidates = List.of(candidate("ore", 9, false, false, Material.DIAMOND_ORE));

        assertEquals(List.of(), TreasureTargetSelector.select(
                candidates, 0, 0, 0, 8, 0, Set.of(Material.DIAMOND_ORE), 10, 10).targets());
        var extended = TreasureTargetSelector.select(
                candidates, 0, 0, 0, 8, 2, Set.of(Material.DIAMOND_ORE), 10, 10);

        assertEquals(10.0, extended.radius());
        assertEquals(List.of("ore"), extended.targets());
    }

    @Test
    void scanAndTargetCapsAreHardLimits() {
        var candidates = List.of(
                candidate("one", 1, false, false, Material.DIAMOND_ORE),
                candidate("two", 2, false, false, Material.DIAMOND_ORE),
                candidate("three", 3, false, false, Material.DIAMOND_ORE));

        var scanCapped = TreasureTargetSelector.select(
                candidates, 0, 0, 0, 8, 0, Set.of(Material.DIAMOND_ORE), 2, 5);
        var targetCapped = TreasureTargetSelector.select(
                candidates, 0, 0, 0, 8, 0, Set.of(Material.DIAMOND_ORE), 5, 1);

        assertEquals(List.of("one", "two"), scanCapped.targets());
        assertEquals(2, scanCapped.scannedBlocks());
        assertEquals(List.of("one"), targetCapped.targets());
        assertEquals(1, targetCapped.scannedBlocks());
    }

    private static TreasureTargetSelector.Candidate<String> candidate(
            String value,
            double x,
            boolean lootable,
            boolean hasLootTable,
            Material material
    ) {
        return new TreasureTargetSelector.Candidate<>(
                value, x, 0, 0, lootable, hasLootTable, material);
    }
}
