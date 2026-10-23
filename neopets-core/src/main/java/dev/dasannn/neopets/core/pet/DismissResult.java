package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of dismissing an owned pet. */
public sealed interface DismissResult permits DismissResult.Dismissed, DismissResult.NotOwned,
        DismissResult.Dead, DismissResult.InTransit, DismissResult.AlreadyInactive {

    /** The pet was persisted as inactive and despawned. */
    record Dismissed(OwnedPet pet) implements DismissResult {
        public Dismissed {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements DismissResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The owned pet is dead and cannot be dismissed. */
    record Dead(OwnedPet pet) implements DismissResult {
        public Dead {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The owned pet is currently in transit and cannot be dismissed. */
    record InTransit(OwnedPet pet) implements DismissResult {
        public InTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet is alive but is not active. */
    record AlreadyInactive(OwnedPet pet) implements DismissResult {
        public AlreadyInactive {
            Objects.requireNonNull(pet, "pet");
        }
    }
}
