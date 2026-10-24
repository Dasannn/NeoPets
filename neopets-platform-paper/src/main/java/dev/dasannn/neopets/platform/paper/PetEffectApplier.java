package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import org.bukkit.entity.LivingEntity;

/** Applies skill-tree effects to a spawned pet without coupling the entity gateway to that module. */
@FunctionalInterface
public interface PetEffectApplier {
    /** Applies the current effects for {@code petId} to {@code entity}. */
    void apply(PetId petId, LivingEntity entity);

    /** Removes live effects before a pet is dismissed, even when its entity can no longer be resolved. */
    default void remove(PetId petId) {
    }

    /** Returns an applier used until the skill-tree adapter is installed. */
    static PetEffectApplier noOp() {
        return (petId, entity) -> { };
    }
}
