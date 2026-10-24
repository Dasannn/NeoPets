package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import java.util.Optional;

/** Computed presentation state and optional action for one configured level. */
public record SkillNodeView(SkillNodeState state, SkillNodeReason reason, Optional<String> actionId) {
    public SkillNodeView {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actionId, "actionId");
        boolean validReason = switch (state) {
            case ACTIVE, NEXT_UNLOCKABLE -> reason == SkillNodeReason.NONE;
            case UNLOCKED -> reason == SkillNodeReason.NONE || reason == SkillNodeReason.PREVIOUS_ACTIVATION;
            case LOCKED -> reason == SkillNodeReason.PREVIOUS_LEVEL;
            case UNAVAILABLE -> reason != SkillNodeReason.NONE
                    && reason != SkillNodeReason.PREVIOUS_LEVEL
                    && reason != SkillNodeReason.PREVIOUS_ACTIVATION;
        };
        if (!validReason) {
            throw new IllegalArgumentException("reason does not match node state");
        }
    }
}
