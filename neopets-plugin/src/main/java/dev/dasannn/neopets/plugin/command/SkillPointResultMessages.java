package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.skilltree.SkillPointResult;
import java.util.Map;
import java.util.Objects;

/** Exhaustive localized-message mapping for skill-point administration outcomes. */
public final class SkillPointResultMessages {
    private SkillPointResultMessages() {
    }

    /** Maps one balance mutation result to an administrator-facing message. */
    public static MessageSpec change(SkillPointResult result, String playerName) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(playerName, "playerName");
        return switch (result) {
            case SkillPointResult.Changed changed -> new MessageSpec("skills.points-changed", Map.of(
                    "player", playerName, "points", Integer.toString(changed.balance())));
            case SkillPointResult.InsufficientSkillPoints insufficient -> new MessageSpec(
                    "skills.points-insufficient", Map.of(
                    "player", playerName,
                    "points", Integer.toString(insufficient.balance()),
                    "amount", Integer.toString(insufficient.requested())));
            case SkillPointResult.Overflow ignored -> new MessageSpec(
                    "skills.points-overflow", Map.of("player", playerName));
        };
    }
}
