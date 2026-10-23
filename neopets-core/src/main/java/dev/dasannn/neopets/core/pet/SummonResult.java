package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of summoning an owned pet. */
public sealed interface SummonResult permits SummonResult.Summoned, SummonResult.NotOwned,
        SummonResult.Dead, SummonResult.InTransit, SummonResult.LimitReached,
        SummonResult.AlreadyActive {

    /** The pet was persisted as active and spawned. */
    record Summoned(OwnedPet pet) implements SummonResult {
        public Summoned {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements SummonResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The owned pet is dead and cannot be summoned. */
    record Dead(OwnedPet pet) implements SummonResult {
        public Dead {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The owned pet is currently in transit and cannot be summoned. */
    record InTransit(OwnedPet pet) implements SummonResult {
        public InTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The player's active-pet rank limit has been reached. */
    record LimitReached(int maxActivePets) implements SummonResult {
        public LimitReached {
            if (maxActivePets < 0) {
                throw new IllegalArgumentException("maxActivePets must not be negative");
            }
        }
    }

    /** The pet is already active. */
    record AlreadyActive(OwnedPet pet) implements SummonResult {
        public AlreadyActive {
            Objects.requireNonNull(pet, "pet");
        }
    }
}
