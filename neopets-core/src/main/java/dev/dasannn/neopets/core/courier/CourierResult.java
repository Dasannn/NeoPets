package dev.dasannn.neopets.core.courier;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.util.Objects;

/** Exhaustive outcomes produced by courier state transitions. */
public sealed interface CourierResult {
    /** A request was persisted and is waiting for its recipient. */
    record Requested(CourierRequest request) implements CourierResult {
        public Requested { Objects.requireNonNull(request, "request"); }
    }

    /** The recipient accepted and the donkey was placed beside them. */
    record Accepted(CourierRequest request) implements CourierResult {
        public Accepted { Objects.requireNonNull(request, "request"); }
    }

    /** The trip ended and the donkey returned to its original state. */
    record Returned(CourierRequest request, OwnedPet pet, Reason reason) implements CourierResult {
        public Returned {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(pet, "pet");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The caller does not own the selected pet. */
    record NotOwned(PetId petId) implements CourierResult {
        public NotOwned { Objects.requireNonNull(petId, "petId"); }
    }

    /** The selected pet is dead, already travelling, or has another request. */
    record Unavailable(PetId petId) implements CourierResult {
        public Unavailable { Objects.requireNonNull(petId, "petId"); }
    }

    /** No matching request exists for this recipient. */
    record UnknownRequest() implements CourierResult { }

    /** A recipient other than the addressed player attempted the transition. */
    record WrongRecipient() implements CourierResult { }

    /** The recipient attempted to accept an already accepted request. */
    record AlreadyAccepted(CourierRequest request) implements CourierResult {
        public AlreadyAccepted { Objects.requireNonNull(request, "request"); }
    }

    /** The pet is still inside its configured sending cooldown. */
    record CoolingDown(Duration remaining) implements CourierResult {
        public CoolingDown {
            Objects.requireNonNull(remaining, "remaining");
            if (remaining.isNegative()) throw new IllegalArgumentException("remaining must not be negative");
        }
    }

    /** Why a request or accepted delivery returned. */
    enum Reason { RECIPIENT_RETURNED, REJECTED, TIMED_OUT, RECIPIENT_QUIT, RECIPIENT_WORLD_CHANGE, OWNER_QUIT }
}
