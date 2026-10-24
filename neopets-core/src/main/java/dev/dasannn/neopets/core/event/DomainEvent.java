package dev.dasannn.neopets.core.event;

/** A fact emitted synchronously after a NeoPets domain state change. */
public sealed interface DomainEvent permits PetGiven, PetSummoned, PetDismissed, PetDied, PetRevived,
        NodeUnlocked, NodeActivated, TreeReset, SkillPointsChanged {
}
