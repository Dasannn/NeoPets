package dev.dasannn.neopets.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public entry point for querying read-only NeoPets data.
 *
 * <p>All methods may be called from any thread, and implementations must be thread-safe. Reference
 * arguments must not be {@code null}; implementations reject them with a {@link
 * NullPointerException}.</p>
 *
 * <p>Methods that can require persistent storage return a {@link CompletableFuture}. Callers
 * must not assume that those futures complete on the server's main thread.</p>
 */
public interface NeoPetsApi {

    /**
     * Finds a pet in the currently loaded data.
     *
     * @param petId the unique pet instance identifier
     * @return the loaded pet, or an empty optional when it is not loaded or does not exist
     * @throws NullPointerException if {@code petId} is {@code null}
     */
    Optional<PetView> findLoadedPet(UUID petId);

    /**
     * Returns the pets currently summoned for a player.
     *
     * @param ownerId the unique player identifier
     * @return an immutable snapshot list of summoned pets
     * @throws NullPointerException if {@code ownerId} is {@code null}
     */
    List<PetView> activePets(UUID ownerId);

    /**
     * Loads a player's NeoPets data.
     *
     * @param ownerId the unique player identifier
     * @return a future containing the player data, or an empty optional when no profile exists
     * @throws NullPointerException if {@code ownerId} is {@code null}
     */
    CompletableFuture<Optional<PlayerPetsView>> loadPlayerPets(UUID ownerId);

    /**
     * Loads the skill-tree progress for a pet.
     *
     * @param petId the unique pet instance identifier
     * @return a future containing the skill tree, or an empty optional when the pet does not exist
     * @throws NullPointerException if {@code petId} is {@code null}
     */
    CompletableFuture<Optional<SkillTreeView>> loadSkillTree(UUID petId);
}
