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
    /** Finds a pet by its instance identifier. */
    CompletableFuture<Optional<OwnedPet>> findById(PetId petId);

    /** Finds all pets owned by a player. */
    CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId);

    /**
     * Inserts or updates a pet.
     *
     * @return a future that fails with {@link DuplicatePetVariantException} when a different pet
     *         already has the same owner and variant
     */
    CompletableFuture<Void> save(OwnedPet pet);

    /** Deletes a pet by its instance identifier. */
    CompletableFuture<Void> deleteById(PetId petId);
}
