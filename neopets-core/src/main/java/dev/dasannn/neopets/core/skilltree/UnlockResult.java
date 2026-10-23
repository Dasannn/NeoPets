package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.math.BigDecimal;
import java.util.Objects;

/** Business outcome of purchasing the next level of a skill-tree attribute. */
public sealed interface UnlockResult permits UnlockResult.Unlocked, UnlockResult.NotOwned,
        UnlockResult.PetDead, UnlockResult.PetInTransit, UnlockResult.UnknownAttribute,
        UnlockResult.MaxLevel, UnlockResult.NodeLimitReached, UnlockResult.InsufficientFunds,
        UnlockResult.Failed {

    /** The next level was paid for and persisted. */
    record Unlocked(TreeProgress progress, String attributeId, int level, BigDecimal moneyCost)
            implements UnlockResult {
        public Unlocked {
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(attributeId, "attributeId");
            Objects.requireNonNull(moneyCost, "moneyCost");
            requireAttributeId(attributeId);
            if (level <= 0) {
                throw new IllegalArgumentException("level must be greater than zero");
            }
            if (moneyCost.signum() < 0) {
                throw new IllegalArgumentException("moneyCost must not be negative");
            }
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements UnlockResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The owned pet is dead. */
    record PetDead(OwnedPet pet) implements UnlockResult {
        public PetDead {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The owned pet is in transit and therefore not alive for tree mutations. */
    record PetInTransit(OwnedPet pet) implements UnlockResult {
        public PetInTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The attribute is not defined for this pet's type. */
    record UnknownAttribute(String attributeId) implements UnlockResult {
        public UnknownAttribute {
            Objects.requireNonNull(attributeId, "attributeId");
            requireAttributeId(attributeId);
        }
    }

    /** Every configured level of the attribute is already unlocked. */
    record MaxLevel(String attributeId, int maxLevel) implements UnlockResult {
        public MaxLevel {
            Objects.requireNonNull(attributeId, "attributeId");
            requireAttributeId(attributeId);
            if (maxLevel < 1) {
                throw new IllegalArgumentException("maxLevel must be at least one");
            }
        }
    }

    /** Unlocking another node would exceed the player's per-pet node limit. */
    record NodeLimitReached(int maxNodesPerPet) implements UnlockResult {
        public NodeLimitReached {
            if (maxNodesPerPet < 0) {
                throw new IllegalArgumentException("maxNodesPerPet must not be negative");
            }
        }
    }

    /** The player's economy balance does not cover the next level's price. */
    record InsufficientFunds(BigDecimal required) implements UnlockResult {
        public InsufficientFunds {
            Objects.requireNonNull(required, "required");
            if (required.signum() < 0) {
                throw new IllegalArgumentException("required must not be negative");
            }
        }
    }

    /** An infrastructure failure prevented the operation from completing safely. */
    record Failed(Throwable cause) implements UnlockResult {
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
