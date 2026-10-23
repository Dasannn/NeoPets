package dev.dasannn.neopets.platform.paper.ability;

import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

/** Pure selection rules shared by the Buscatesoros world scanner and its tests. */
public final class TreasureTargetSelector {
    private TreasureTargetSelector() {
    }

    /** Adds the configured base radius and the active scent bonus. */
    public static double effectiveRadius(double baseRadius, double scentBonus) {
        if (!Double.isFinite(baseRadius) || baseRadius <= 0) {
            throw new IllegalArgumentException("baseRadius must be finite and positive");
        }
        if (!Double.isFinite(scentBonus) || scentBonus < 0) {
            throw new IllegalArgumentException("scentBonus must be finite and non-negative");
        }
        return baseRadius + scentBonus;
    }

    /** Returns whether a block is an unopened loot container or a configured ore. */
    public static boolean isTreasure(
            boolean lootable,
            boolean hasLootTable,
            Material material,
            Set<Material> oreMaterials
    ) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(oreMaterials, "oreMaterials");
        return lootable && hasLootTable || oreMaterials.contains(material);
    }

    /** Tests the spherical range used by the incremental runtime scan. */
    static boolean withinRadius(double x, double y, double z,
                                double originX, double originY, double originZ, double radius) {
        double dx = x - originX;
        double dy = y - originY;
        double dz = z - originZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
