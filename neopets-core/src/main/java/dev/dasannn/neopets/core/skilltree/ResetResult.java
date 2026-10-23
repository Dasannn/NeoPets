package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Objects;

/** Business outcome of resetting all levels of a pet's skill tree. */
public sealed interface ResetResult permits ResetResult.Reset, ResetResult.NotOwned, ResetResult.Failed {
    /** The zeroed progress was persisted and the requested skill points were returned. */
    record Reset(TreeProgress progress, int refundedSkillPoints) implements ResetResult {
        public Reset {
            Objects.requireNonNull(progress, "progress");
            if (refundedSkillPoints < 0) {
                throw new IllegalArgumentException("refundedSkillPoints must not be negative");
            }
        }
    }

    /** The pet does not exist or belongs to another player. */
    record NotOwned(PetId petId) implements ResetResult {
        public NotOwned {
            Objects.requireNonNull(petId, "petId");
        }
    }

    /** An infrastructure failure prevented the operation from completing safely. */
    record Failed(Throwable cause) implements ResetResult {
        public Failed {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
