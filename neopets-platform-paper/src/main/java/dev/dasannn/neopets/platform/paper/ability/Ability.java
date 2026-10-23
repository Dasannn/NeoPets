package dev.dasannn.neopets.platform.paper.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** A custom skill-tree ability attached to a live pet while its custom attribute is active. */
public interface Ability {
    /** Returns the custom effect identifier used by the skill-tree YAML. */
    String id();

    /** Enables this ability for a live pet at the given active level. */
    void onActivate(LivingEntity pet, Player owner, int level);

    /** Disables this ability for a live pet. */
    void onDeactivate(LivingEntity pet, Player owner, int level);

    /** Performs optional shared-ticker work. */
    default void tick(AbilityContext context) {
    }
}
