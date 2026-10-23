package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a pet death.
 */
public interface PetDeathEvent {

    /**
     * Returns a snapshot of the dead pet.
     *
     * @return the dead pet
     */
    PetView pet();
}
