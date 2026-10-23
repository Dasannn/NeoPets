package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence for opaque, platform-serialized pet inventory contents. */
public interface PetStorageRepository {
    /**
     * Returns a defensive copy of the stored contents, or an empty optional when no contents exist.
     */
    CompletableFuture<Optional<byte[]>> load(PetId petId);

    /**
     * Inserts or replaces the contents for a pet.
     *
     * <p>The implementation must snapshot {@code contents} before returning so later caller mutations cannot affect
     * the pending write.</p>
     *
     * @return a future completed after the change is durable, or completed exceptionally on failure
     */
    CompletableFuture<Void> save(PetId petId, byte[] contents);

    /** Deletes the contents if present; deleting an unknown identifier succeeds without changing data. */
    CompletableFuture<Void> deleteById(PetId petId);
}
