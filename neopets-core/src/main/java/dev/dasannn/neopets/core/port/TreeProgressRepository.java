package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence operations for a pet's skill-tree progress. */
public interface TreeProgressRepository {
    /** Returns the stored progress, or an empty optional when the pet has no progress yet. */
    CompletableFuture<Optional<TreeProgress>> load(PetId petId);

    /**
     * Inserts or replaces all skill-tree progress for a pet in one durable write.
     *
     * @return a future completed after the change is durable, or completed exceptionally on failure
     */
    CompletableFuture<Void> save(TreeProgress progress);
}
