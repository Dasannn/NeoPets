package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence operations for owned pets. */
public interface PetRepository {
    CompletableFuture<Optional<OwnedPet>> findById(PetId petId);

    CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId);

    CompletableFuture<Void> save(OwnedPet pet);

    CompletableFuture<Void> deleteById(PetId petId);
}
