package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.concurrent.CompletableFuture;

/**
 * Platform-neutral lifecycle operations for the entity representing a pet.
 *
 * <p>Methods may be called from any thread. Implementations are responsible for scheduling entity work on the
 * server thread required by their platform. Each returned future completes only after that work has finished.</p>
 */
public interface PetEntityGateway {
    /** Spawns the entity for an owned pet that does not currently have one. */
    CompletableFuture<Void> spawn(PetId petId);

    /** Spawns an invulnerable courier pet next to its addressed online recipient. */
    default CompletableFuture<Void> spawnForCourier(PetId petId, PlayerId recipient) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("courier spawning is unavailable"));
    }

    /** Removes the pet's entity if one is currently spawned. */
    CompletableFuture<Void> despawn(PetId petId);

    /** Reapplies the current domain effects to the pet's spawned entity. */
    CompletableFuture<Void> applyEffects(PetId petId);
}
