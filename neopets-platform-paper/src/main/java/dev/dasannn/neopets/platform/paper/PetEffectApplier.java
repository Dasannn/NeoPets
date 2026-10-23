package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import org.bukkit.entity.LivingEntity;

/** Applies skill-tree effects to a spawned pet without coupling the entity gateway to that module. */
@FunctionalInterface
public interface PetEffectApplier {
    /** Applies the current effects for {@code petId} to {@code entity}. */
    void apply(PetId petId, LivingEntity entity);

    /** Removes live effects before a pet entity is dismissed. */
    default void remove(PetId petId, LivingEntity entity) {
    }

    /** Returns an applier used until the skill-tree adapter is installed. */
    static PetEffectApplier noOp() {
        return (petId, entity) -> { };
    }
}
