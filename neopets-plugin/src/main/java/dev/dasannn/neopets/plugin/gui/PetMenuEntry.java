package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.PetStatus;
import java.util.Objects;

/** Pure display data for one owned pet in the pets menu. */
public record PetMenuEntry(
        String petId,
        String typeId,
        String displayName,
        String iconMaterial,
        boolean baby,
        PetStatus status,
        boolean active,
        int unlockedNodes,
        int assignedSkillPoints,
        boolean courierSaddlebagsActive
) {
    public PetMenuEntry {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(typeId, "typeId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(iconMaterial, "iconMaterial");
        Objects.requireNonNull(status, "status");
        if (petId.isBlank() || typeId.isBlank() || displayName.isBlank() || iconMaterial.isBlank()) {
            throw new IllegalArgumentException("pet display strings must not be blank");
        }
        if (unlockedNodes < 0 || assignedSkillPoints < 0) {
            throw new IllegalArgumentException("pet progress must not be negative");
        }
    }

    public boolean dead() {
        return status == PetStatus.DEAD;
    }
}
