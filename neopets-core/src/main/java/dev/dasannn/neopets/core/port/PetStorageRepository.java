package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence for opaque, platform-serialized pet inventory contents. */
public interface PetStorageRepository {
    CompletableFuture<Optional<byte[]>> load(PetId petId);

    CompletableFuture<Void> save(PetId petId, byte[] contents);

    CompletableFuture<Void> deleteById(PetId petId);
}
