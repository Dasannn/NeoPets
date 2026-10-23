package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Asynchronous persistence for soul items waiting to reach an owner. */
public interface PendingSoulDeliveryRepository {
    /** Inserts or idempotently retains the pending delivery identified by its unique token. */
    CompletableFuture<Void> save(PendingSoulDelivery delivery);

    /** Returns every pending delivery for an owner in creation order. */
    CompletableFuture<List<PendingSoulDelivery>> findByOwner(PlayerId ownerId);

    /** Removes a delivery only after its item has reached the inventory or the owner's feet. */
    CompletableFuture<Void> delete(UUID tokenId);
}
