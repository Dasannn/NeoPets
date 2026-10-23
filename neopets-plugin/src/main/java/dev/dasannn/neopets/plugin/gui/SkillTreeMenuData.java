package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Complete server-independent snapshot used to build one pet's skill-tree menu. */
public record SkillTreeMenuData(
        String petId,
        String petName,
        String typeId,
        String iconMaterial,
        boolean baby,
        int availableSkillPoints,
        int assignedSkillPoints,
        int maxSkillPoints,
        int unlockedNodes,
        int maxNodes,
        Optional<BigDecimal> economyBalance,
        SkillTreeDefinition definition,
        TreeProgress progress,
        boolean resetEnabled,
        BigDecimal resetCost
) {
    public SkillTreeMenuData {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(petName, "petName");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(iconMaterial, "iconMaterial");
        Objects.requireNonNull(economyBalance, "economyBalance");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(resetCost, "resetCost");
        if (petId.isBlank() || petName.isBlank() || typeId.isBlank() || iconMaterial.isBlank()) {
            throw new IllegalArgumentException("pet display strings must not be blank");
        }
        if (availableSkillPoints < 0 || assignedSkillPoints < 0 || maxSkillPoints < 0
                || unlockedNodes < 0 || maxNodes < 0 || resetCost.signum() < 0) {
            throw new IllegalArgumentException("balances, limits and costs must not be negative");
        }
        if (!definition.petTypeId().value().equals(typeId) || !progress.petId().toString().equals(petId)) {
            throw new IllegalArgumentException("pet identity must match definition and progress");
        }
        if (assignedSkillPoints != progress.assignedSkillPoints(definition)
                || unlockedNodes != progress.unlockedNodes()) {
            throw new IllegalArgumentException("summary counts must match progress");
        }
    }
}
