package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a pet being given to a player.
 */
public interface PetGiveEvent {

    /**
     * Returns a snapshot of the pet that was given.
     *
     * @return the given pet
     */
    PetView pet();
}
