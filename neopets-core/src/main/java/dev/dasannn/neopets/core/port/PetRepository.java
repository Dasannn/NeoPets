package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous persistence operations for owned pets.
 *
 * <p>A pet row is the source of truth for ownership. Implementations must enforce uniqueness of
 * {@code (owner, variant)} so separate service instances cannot grant one variant twice.</p>
 */
public interface PetRepository {
    /** Returns the stored pet, or an empty optional when the identifier is unknown. */
    CompletableFuture<Optional<OwnedPet>> findById(PetId petId);

    /** Returns a snapshot of all pets owned by the player. */
    CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId);

    /**
     * Inserts or replaces a pet by its identifier.
     *
     * @return a future completed after the change is durable; it fails with
     *         {@link DuplicatePetVariantException} when a different pet already has the same owner
     *         and variant, or exceptionally on any other failure
     */
    CompletableFuture<Void> save(OwnedPet pet);

    /** Deletes the pet if present; deleting an unknown identifier succeeds without changing data. */
    CompletableFuture<Void> deleteById(PetId petId);
}
