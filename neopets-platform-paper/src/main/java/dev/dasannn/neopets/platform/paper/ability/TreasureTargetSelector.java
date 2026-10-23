package dev.dasannn.neopets.platform.paper.ability;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    /** Selects range-limited treasures while enforcing both work and result caps. */
    public static <T> Selection<T> select(
            Collection<Candidate<T>> candidates,
            double originX,
            double originY,
            double originZ,
            double baseRadius,
            double scentBonus,
            Set<Material> oreMaterials,
            int maxScanBlocks,
            int maxTargets
    ) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(oreMaterials, "oreMaterials");
        if (maxScanBlocks < 1 || maxTargets < 1) {
            throw new IllegalArgumentException("scan and target caps must be positive");
        }
        double radius = effectiveRadius(baseRadius, scentBonus);
        double radiusSquared = radius * radius;
        int scanned = 0;
        var selected = new ArrayList<T>(Math.min(maxTargets, candidates.size()));
        for (Candidate<T> candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (scanned >= maxScanBlocks || selected.size() >= maxTargets) {
                break;
            }
            scanned++;
            double dx = candidate.x() - originX;
            double dy = candidate.y() - originY;
            double dz = candidate.z() - originZ;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared
                    && isTreasure(candidate.lootable(), candidate.hasLootTable(),
                    candidate.material(), oreMaterials)) {
                selected.add(candidate.target());
            }
        }
        return new Selection<>(selected, scanned, radius);
    }

    /** Immutable block facts consumed by the pure selection rule. */
    public record Candidate<T>(
            T target,
            double x,
            double y,
            double z,
            boolean lootable,
            boolean hasLootTable,
            Material material
    ) {
        public Candidate {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(material, "material");
        }
    }

    /** Immutable bounded selection result. */
    public record Selection<T>(List<T> targets, int scannedBlocks, double radius) {
        public Selection {
            targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        }
    }
}
