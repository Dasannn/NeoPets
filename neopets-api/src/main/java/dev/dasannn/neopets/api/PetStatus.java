package dev.dasannn.neopets.api;

/**
 * Describes the lifecycle state of a pet without exposing implementation-specific types.
 */
public enum PetStatus {
    /** The pet is alive and available to its owner. */
    ALIVE,

    /** The pet is dead and must be revived before it can be summoned. */
    DEAD,

    /** The pet is temporarily visiting another player. */
    IN_TRANSIT
}
