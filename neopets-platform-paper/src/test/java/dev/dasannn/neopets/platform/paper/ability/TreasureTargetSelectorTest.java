package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TreasureTargetSelectorTest {
    @Test
    void selectsOnlyUnopenedLootablesAndConfiguredOres() {
        var ores = Set.of(Material.DIAMOND_ORE);
        assertTrue(TreasureTargetSelector.isTreasure(true, true, Material.CHEST, ores));
        assertFalse(TreasureTargetSelector.isTreasure(true, false, Material.CHEST, ores));
        assertFalse(TreasureTargetSelector.isTreasure(false, false, Material.BARREL, ores));
        assertTrue(TreasureTargetSelector.isTreasure(false, false, Material.DIAMOND_ORE, ores));
        assertFalse(TreasureTargetSelector.isTreasure(false, false, Material.IRON_ORE, ores));
    }
}
