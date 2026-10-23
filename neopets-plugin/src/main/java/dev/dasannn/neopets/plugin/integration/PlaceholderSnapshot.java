package dev.dasannn.neopets.plugin.integration;

import java.util.Objects;
import java.util.Optional;

/** Immutable data used to resolve every NeoPets placeholder without I/O. */
public record PlaceholderSnapshot(
        int skillPoints,
        int maxSkillPointsPerPet,
        int ownedPets,
        int activePets,
        int maxActivePets,
        Optional<String> firstActivePetName
) {
    public PlaceholderSnapshot {
        Objects.requireNonNull(firstActivePetName, "firstActivePetName");
        if (skillPoints < 0 || maxSkillPointsPerPet < 0 || ownedPets < 0
                || activePets < 0 || maxActivePets < 0) {
            throw new IllegalArgumentException("placeholder counts and limits must not be negative");
        }
        if (activePets > ownedPets) {
            throw new IllegalArgumentException("activePets must not exceed ownedPets");
        }
        firstActivePetName.ifPresent(name -> {
            if (name.isBlank()) {
                throw new IllegalArgumentException("firstActivePetName must not be blank when present");
            }
        });
    }
}
