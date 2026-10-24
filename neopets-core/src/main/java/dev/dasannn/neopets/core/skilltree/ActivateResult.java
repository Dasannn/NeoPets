package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of activating the next purchased level of an attribute. */
public sealed interface ActivateResult permits ActivateResult.Activated, ActivateResult.NotOwned,
        ActivateResult.PetDead, ActivateResult.PetInTransit, ActivateResult.UnknownAttribute,
        ActivateResult.NoUnlockedLevel, ActivateResult.SkillPointLimitReached,
        ActivateResult.InsufficientSkillPoints, ActivateResult.Failed {

    /** The next unlocked level was activated and persisted. */
    record Activated(TreeProgress progress, String attributeId, int level, int skillPointCost)
            implements ActivateResult {
        public Activated {
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(attributeId, "attributeId");
            requireAttributeId(attributeId);
            if (level <= 0) {
                throw new IllegalArgumentException("level must be greater than zero");
            }
            if (skillPointCost < 0) {
                throw new IllegalArgumentException("skillPointCost must not be negative");
            }
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements ActivateResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The owned pet is dead. */
    record PetDead(OwnedPet pet) implements ActivateResult {
        public PetDead {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The owned pet is in transit and therefore not alive for tree mutations. */
    record PetInTransit(OwnedPet pet) implements ActivateResult {
        public PetInTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The attribute is not defined for this pet's type. */
    record UnknownAttribute(String attributeId) implements ActivateResult {
        public UnknownAttribute {
            Objects.requireNonNull(attributeId, "attributeId");
            requireAttributeId(attributeId);
        }
    }

    /** The attribute has no purchased but inactive level to activate. */
    record NoUnlockedLevel(String attributeId) implements ActivateResult {
        public NoUnlockedLevel {
            Objects.requireNonNull(attributeId, "attributeId");
            requireAttributeId(attributeId);
        }
    }

    /** Activating the level would exceed the player's per-pet assigned-SP limit. */
    record SkillPointLimitReached(int maxSkillPointsPerPet) implements ActivateResult {
        public SkillPointLimitReached {
            if (maxSkillPointsPerPet < 0) {
                throw new IllegalArgumentException("maxSkillPointsPerPet must not be negative");
            }
        }
    }

    /** The player's unassigned skill-point balance does not cover the level's cost. */
    record InsufficientSkillPoints(int required, int available) implements ActivateResult {
        public InsufficientSkillPoints {
            if (required < 0 || available < 0) {
                throw new IllegalArgumentException("skill-point amounts must not be negative");
            }
        }
    }

    /** An infrastructure failure prevented the operation from completing safely. */
    record Failed(Throwable cause) implements ActivateResult {
        public Failed {
            Objects.requireNonNull(cause, "cause");
        }
    }

    private static void requireAttributeId(String attributeId) {
        if (attributeId.isBlank()) {
            throw new IllegalArgumentException("attributeId must not be blank");
        }
    }
}
