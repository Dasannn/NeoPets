package dev.dasannn.neopets.plugin.gui;

import java.util.List;
import java.util.Objects;

/** Complete server-independent snapshot used to build the pets menu. */
public record PetsMenuData(
        List<PetMenuEntry> pets,
        int skillPointBalance,
        int maxActivePets,
        int maxSkillPointsPerPet,
        int maxNodesPerPet
) {
    public PetsMenuData {
        Objects.requireNonNull(pets, "pets");
        pets = List.copyOf(pets);
        if (skillPointBalance < 0 || maxActivePets < 0 || maxSkillPointsPerPet < 0 || maxNodesPerPet < 0) {
            throw new IllegalArgumentException("balances and limits must not be negative");
        }
    }
}
