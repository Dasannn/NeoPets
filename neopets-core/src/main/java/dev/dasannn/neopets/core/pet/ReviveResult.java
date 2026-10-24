package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of changing a dead pet back to the alive, inactive state. */
public sealed interface ReviveResult
        permits ReviveResult.Revived, ReviveResult.AlreadyAlive, ReviveResult.NotOwned,
                ReviveResult.InTransit, ReviveResult.InvalidSoul {
    /** The pet was persisted as alive and inactive. */
    record Revived(OwnedPet pet) implements ReviveResult {
        public Revived {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet is already alive. */
    record AlreadyAlive(OwnedPet pet) implements ReviveResult {
        public AlreadyAlive {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements ReviveResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The pet is in courier transit and cannot be revived. */
    record InTransit(OwnedPet pet) implements ReviveResult {
        public InTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The supplied soul token was absent, mismatched, or had already been consumed. */
    record InvalidSoul(PetId petId) implements ReviveResult {
        public InvalidSoul {
            Objects.requireNonNull(petId, "petId");
        }
    }
}
