package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PetId;

/** Platform-neutral lifecycle operations for the entity representing a pet. */
public interface PetEntityGateway {
    void spawn(PetId petId);

    void despawn(PetId petId);

    void applyEffects(PetId petId);
}
