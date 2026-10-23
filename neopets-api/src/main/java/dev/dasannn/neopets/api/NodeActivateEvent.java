package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a skill-tree node activation.
 */
public interface NodeActivateEvent {

    /**
     * Returns a snapshot of the pet whose node was activated.
     *
     * @return the affected pet
     */
    PetView pet();

    /**
     * Returns the configured identifier of the affected attribute.
     *
     * @return the attribute identifier
     */
    String attributeId();

    /**
     * Returns the one-based level of the node that was activated.
     *
     * @return the activated level
     */
    int level();
}
