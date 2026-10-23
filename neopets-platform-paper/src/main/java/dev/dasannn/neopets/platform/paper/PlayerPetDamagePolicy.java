package dev.dasannn.neopets.platform.paper;

import java.util.UUID;

/** Extension point for player-versus-pet damage rules. */
@FunctionalInterface
public interface PlayerPetDamagePolicy {
    /** Returns whether damage from {@code attackingPlayerId} to a pet owned by {@code ownerId} is denied. */
    boolean denies(UUID ownerId, UUID attackingPlayerId);

    /** Current MVP rule: owners cannot damage their own pets; third-party PvP is decided by a later task. */
    static PlayerPetDamagePolicy ownerCannotDamage() {
        return UUID::equals;
    }
}
