package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;

/**
 * Platform-neutral lifecycle operations for the entity representing a pet.
 *
 * <p>Operations are synchronous. Callers must invoke them from a thread on which the platform permits entity access;
 * implementations do not implicitly schedule work.</p>
 */
public interface PetEntityGateway {
    /** Spawns the entity for an owned pet that does not currently have one. */
    void spawn(PetId petId);

    /** Removes the pet's entity if one is currently spawned. */
    void despawn(PetId petId);

    /** Reapplies the current domain effects to the pet's spawned entity. */
    void applyEffects(PetId petId);
}
