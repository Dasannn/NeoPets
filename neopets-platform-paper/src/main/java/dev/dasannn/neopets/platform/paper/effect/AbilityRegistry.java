package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Platform seam through which custom skill-tree abilities receive their current active level. */
@FunctionalInterface
public interface AbilityRegistry {
    /**
     * Updates a custom ability for a live pet.
     *
     * @param abilityId configured custom effect identifier
     * @param petId persistent pet identifier
     * @param entity live Paper entity
     * @param activeLevel current active level, or zero to disable the ability
     * @param parameters opaque parameters for the active level, empty when inactive or unconfigured
     * @return whether an ability with this identifier is registered
     */
    boolean update(
            String abilityId,
            PetId petId,
            LivingEntity entity,
            int activeLevel,
            Map<String, String> parameters
    );

    /** Ticks every active ability attached to this live pet from the shared pet ticker. */
    default void tick(PetId petId, LivingEntity entity, Player owner) {
    }

    /** Deactivates and forgets every ability attached to one pet. */
    default void deactivate(PetId petId) {
    }

    /** Deactivates and forgets all registered pet activations. */
    default void deactivateAll() {
    }

    /** Returns a registry containing no abilities. */
    static AbilityRegistry empty() {
        return (abilityId, petId, entity, activeLevel, parameters) -> false;
    }
}
