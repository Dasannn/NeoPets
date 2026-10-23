package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of changing a live pet to the dead state. */
public sealed interface DeathResult
        permits DeathResult.Died, DeathResult.AlreadyDead, DeathResult.NotOwned, DeathResult.InTransit {
    /** The pet was persisted as dead and inactive. */
    record Died(OwnedPet pet) implements DeathResult {
        public Died {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet had already completed its death transition. */
    record AlreadyDead(OwnedPet pet) implements DeathResult {
        public AlreadyDead {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements DeathResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** The pet is in courier transit and cannot die. */
    record InTransit(OwnedPet pet) implements DeathResult {
        public InTransit {
            Objects.requireNonNull(pet, "pet");
        }
    }
}
