package dev.dasannn.neopets.platform.paper.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Passive registration point for the donkey {@code courier_saddlebags} custom effect. */
public final class CourierSaddlebagsAbility implements Ability {
    public static final String ID = "courier_saddlebags";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onActivate(LivingEntity pet, Player owner, int level) {
        // The plugin GUI and courier coordinator provide this passive ability's interactions.
    }

    @Override
    public void onDeactivate(LivingEntity pet, Player owner, int level) {
        // No entity-local state is installed.
    }
}
