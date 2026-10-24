package dev.dasannn.neopets.core.courier;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of one donkey courier trip. */
public record CourierRequest(
        UUID id,
        PetId petId,
        PlayerId owner,
        PlayerId recipient,
        Instant expiresAt,
        boolean accepted,
        boolean originallyActive
) {
    public CourierRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (owner.equals(recipient)) {
            throw new IllegalArgumentException("courier recipient must differ from owner");
        }
    }

    /** Returns the accepted form of this request. */
    public CourierRequest accept() {
        return new CourierRequest(id, petId, owner, recipient, expiresAt, true, originallyActive);
    }
}
