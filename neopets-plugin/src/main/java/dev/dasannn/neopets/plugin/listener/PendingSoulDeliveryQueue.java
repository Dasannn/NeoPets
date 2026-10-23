package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PendingSoulDeliveryRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Serializes durable soul-item delivery from enqueue through acknowledgement for each owner.
 *
 * <p>Unacknowledged claims are retried after interruption. A process crash between the platform
 * inventory write and the SQL acknowledgement can repeat the physical item, with the same
 * single-use token; those two systems cannot commit atomically.</p>
 */
final class PendingSoulDeliveryQueue<T> {
    @FunctionalInterface
    interface DeliveryAttempt<T> {
        /** Delivers on the appropriate platform thread, returning false when the owner is offline. */
        CompletableFuture<Boolean> deliverIfAvailable(PlayerId ownerId, T item, OwnedPet pet);
    }

    private final PendingSoulDeliveryRepository pending;
    private final PetRepository pets;
    private final SoulItemService souls;
    private final PlayerOperationQueue operations;
    private final BiFunction<PendingSoulDelivery, OwnedPet, T> itemFactory;
    private final DeliveryAttempt<T> delivery;

    PendingSoulDeliveryQueue(
            PendingSoulDeliveryRepository pending,
            PetRepository pets,
            SoulItemService souls,
            PlayerOperationQueue operations,
            BiFunction<PendingSoulDelivery, OwnedPet, T> itemFactory,
            DeliveryAttempt<T> delivery
    ) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.itemFactory = Objects.requireNonNull(itemFactory, "itemFactory");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
    }

    CompletableFuture<Void> enqueueAndDeliver(SoulToken token) {
        Objects.requireNonNull(token, "token");
        return operations.submit(token.ownerId(), () -> souls.wasDelivered(token).thenCompose(delivered -> {
            if (delivered) {
                return CompletableFuture.completedFuture(null);
            }
            return pending.save(PendingSoulDelivery.from(token))
                    .thenCompose(ignored -> deliverPendingUnsequenced(token.ownerId()));
        }));
    }

    CompletableFuture<Void> deliverPending(PlayerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return operations.submit(ownerId, () -> deliverPendingUnsequenced(ownerId));
    }

    private CompletableFuture<Void> deliverPendingUnsequenced(PlayerId ownerId) {
        return pending.releaseClaims(ownerId).thenCompose(ignored -> pending.findByOwner(ownerId))
                .thenCompose(queued -> deliverInOrder(queued, 0));
    }

    private CompletableFuture<Void> deliverInOrder(List<PendingSoulDelivery> queued, int index) {
        if (index == queued.size()) {
            return CompletableFuture.completedFuture(null);
        }
        PendingSoulDelivery next = queued.get(index);
        return deliverOne(next).thenCompose(ignored -> deliverInOrder(queued, index + 1));
    }

    private CompletableFuture<Void> deliverOne(PendingSoulDelivery queued) {
        SoulToken token = new SoulToken(
                queued.tokenId(), queued.petId(), queued.ownerId(), queued.createdAt());
        return souls.wasDelivered(token).thenCompose(delivered -> {
            if (delivered) {
                return pending.delete(queued.tokenId());
            }
            return pets.findById(queued.petId()).thenCompose(found -> {
                OwnedPet pet = found.orElseThrow(() -> new IllegalStateException(
                        "Missing pet for pending soul delivery " + queued.tokenId()));
                T item = Objects.requireNonNull(itemFactory.apply(queued, pet), "soul item");
                return pending.claim(queued.tokenId()).thenCompose(claimed -> {
                    if (!claimed) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return delivery.deliverIfAvailable(queued.ownerId(), item, pet).thenCompose(wasDelivered -> {
                        if (!wasDelivered) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return souls.markDelivered(token).thenCompose(acknowledged -> {
                            if (!acknowledged) {
                                return CompletableFuture.failedFuture(new IllegalStateException(
                                        "Soul delivery was concurrently acknowledged for " + queued.tokenId()));
                            }
                            return pending.delete(queued.tokenId());
                        });
                    });
                });
            });
        });
    }
}
