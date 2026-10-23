package dev.dasannn.neopets.platform.paper;

/** The single movement action selected for a pet during one shared ticker pass. */
public enum FollowAction {
    /** The pet is close enough and should stop following. */
    IDLE,
    /** The pet should pathfind toward its owner. */
    PATHFIND,
    /** The pet should immediately teleport to its owner. */
    TELEPORT
}
