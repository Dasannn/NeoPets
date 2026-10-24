package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.Objects;

/** Business outcome of giving a pet to a player. */
public sealed interface GiveResult permits GiveResult.Granted, GiveResult.AllOwned,
        GiveResult.UnknownVariant, GiveResult.AlreadyOwned {

    /** A new pet was persisted for the player. */
    record Granted(OwnedPet pet) implements GiveResult {
        public Granted {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** Every variant in the requested candidate set is already owned. */
    record AllOwned() implements GiveResult {
    }

    /** A requested configured variant identifier does not exist. */
    record UnknownVariant(VariantId variantId) implements GiveResult {
        public UnknownVariant {
            Objects.requireNonNull(variantId, "variantId");
        }
    }

    /** The specifically requested variant is already owned. */
    record AlreadyOwned(VariantId variantId) implements GiveResult {
        public AlreadyOwned {
            Objects.requireNonNull(variantId, "variantId");
        }
    }
}
