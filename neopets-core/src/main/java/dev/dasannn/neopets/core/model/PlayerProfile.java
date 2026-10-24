package dev.dasannn.neopets.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** An immutable player's skill-point balance and owned pets. */
public record PlayerProfile(PlayerId playerId, int skillPointBalance, List<OwnedPet> ownedPets) {
    public PlayerProfile {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(ownedPets, "ownedPets");
        if (skillPointBalance < 0) {
            throw new IllegalArgumentException("skillPointBalance must not be negative");
        }
        ownedPets = List.copyOf(ownedPets);

        Set<PetId> petIds = new HashSet<>();
        Set<VariantId> variants = new HashSet<>();
        for (OwnedPet pet : ownedPets) {
            if (!playerId.equals(pet.owner())) {
                throw new IllegalArgumentException("every owned pet must belong to the profile player");
            }
            if (!petIds.add(pet.id())) {
                throw new IllegalArgumentException("owned pets must have unique ids");
            }
            if (!variants.add(pet.variant())) {
                throw new IllegalArgumentException("a player cannot own the same variant twice");
            }
        }
    }

    /** Returns whether this player owns the configured variant. */
    public boolean ownsVariant(VariantId variantId) {
        Objects.requireNonNull(variantId, "variantId");
        return ownedPets.stream().anyMatch(pet -> pet.variant().equals(variantId));
    }

    /** Returns the player's currently summoned pets in ownership order. */
    public List<OwnedPet> activePets() {
        return ownedPets.stream().filter(OwnedPet::active).toList();
    }

    /** Returns a copy with a different skill-point balance. */
    public PlayerProfile withSkillPointBalance(int newBalance) {
        return new PlayerProfile(playerId, newBalance, ownedPets);
    }

    /** Returns a copy with a different owned-pet collection. */
    public PlayerProfile withOwnedPets(List<OwnedPet> newOwnedPets) {
        return new PlayerProfile(playerId, skillPointBalance, newOwnedPets);
    }
}
