package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.Objects;

/**
 * Signals that a pet insert violated the one-pet-per-owner-and-variant constraint.
 *
 * <p>Repositories use this exception to report a storage race to the domain service. The service
 * converts it into an operation result; callers of the service never need to handle it.</p>
 */
public final class DuplicatePetVariantException extends RuntimeException {
    private final PlayerId ownerId;
    private final VariantId variantId;

    /** Creates a uniqueness-conflict signal for an owner and variant. */
    public DuplicatePetVariantException(PlayerId ownerId, VariantId variantId) {
        super("player " + Objects.requireNonNull(ownerId, "ownerId")
                + " already owns variant " + Objects.requireNonNull(variantId, "variantId"));
        this.ownerId = ownerId;
        this.variantId = variantId;
    }

    /** Returns the owner whose unique variant constraint was violated. */
    public PlayerId ownerId() {
        return ownerId;
    }

    /** Returns the duplicate variant. */
    public VariantId variantId() {
        return variantId;
    }
}
