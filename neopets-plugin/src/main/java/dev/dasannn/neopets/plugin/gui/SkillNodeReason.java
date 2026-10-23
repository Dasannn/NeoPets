package dev.dasannn.neopets.plugin.gui;

/** Explanation attached to a node whose next operation is unavailable. */
public enum SkillNodeReason {
    NONE,
    PREVIOUS_LEVEL,
    NODE_LIMIT,
    SKILL_POINT_LIMIT,
    INSUFFICIENT_SKILL_POINTS,
    INSUFFICIENT_FUNDS,
    ECONOMY_UNAVAILABLE
}
