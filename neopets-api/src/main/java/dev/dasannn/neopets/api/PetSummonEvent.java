package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a pet summon.
 */
public interface PetSummonEvent {

    /**
     * Returns a snapshot of the summoned pet.
     *
     * @return the summoned pet
     */
    PetView pet();
}
