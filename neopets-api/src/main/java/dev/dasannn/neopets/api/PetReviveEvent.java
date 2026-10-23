package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a pet revival.
 */
public interface PetReviveEvent {

    /**
     * Returns a snapshot of the revived pet.
     *
     * @return the revived pet
     */
    PetView pet();
}
