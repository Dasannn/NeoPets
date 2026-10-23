package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PetDeathWork;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence for resumable pet-death workflow checkpoints. */
public interface PetDeathWorkRepository {
    /** Inserts or replaces the workflow for a newly observed death. */
    CompletableFuture<Void> save(PetDeathWork work);

    /** Returns the workflow for a pet when one has been started. */
    CompletableFuture<Optional<PetDeathWork>> findByPet(PetId petId);

    /** Returns all workflows owned by a player. */
    CompletableFuture<List<PetDeathWork>> findByOwner(PlayerId ownerId);
}
