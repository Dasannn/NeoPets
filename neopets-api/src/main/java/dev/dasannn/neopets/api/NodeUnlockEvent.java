package dev.dasannn.neopets.api;

/**
 * Public contract for an event describing a skill-tree node unlock.
 */
public interface NodeUnlockEvent {

    /**
     * Returns a snapshot of the pet whose node was unlocked.
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
     * Returns the one-based level of the node that was unlocked.
     *
     * @return the unlocked level
     */
    int level();
}
