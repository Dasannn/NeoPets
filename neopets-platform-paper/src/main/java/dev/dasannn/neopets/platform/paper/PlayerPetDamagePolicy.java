package dev.dasannn.neopets.platform.paper;

import java.util.UUID;

/** Extension point for player-versus-pet damage rules. */
@FunctionalInterface
public interface PlayerPetDamagePolicy {
    /** Returns whether damage from {@code attackingPlayerId} to a pet owned by {@code ownerId} is denied. */
    boolean denies(UUID ownerId, UUID attackingPlayerId);

    /** Base protection rule: owners cannot damage their own pets. */
    static PlayerPetDamagePolicy ownerCannotDamage() {
        return UUID::equals;
    }
}
