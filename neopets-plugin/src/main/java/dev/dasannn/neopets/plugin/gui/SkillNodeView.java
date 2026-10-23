package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import java.util.Optional;

/** Computed presentation state and optional action for one configured level. */
public record SkillNodeView(SkillNodeState state, SkillNodeReason reason, Optional<String> actionId) {
    public SkillNodeView {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actionId, "actionId");
        if (state != SkillNodeState.UNAVAILABLE && reason != SkillNodeReason.NONE
                && !(state == SkillNodeState.LOCKED && reason == SkillNodeReason.PREVIOUS_LEVEL)) {
            throw new IllegalArgumentException("reason does not match node state");
        }
    }
}
