package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of renaming an owned pet. */
public sealed interface RenameResult permits RenameResult.Renamed, RenameResult.NotOwned {

    /** The pet was persisted with its new custom name. */
    record Renamed(OwnedPet pet) implements RenameResult {
        public Renamed {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements RenameResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }
}
