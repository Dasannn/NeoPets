package dev.dasannn.neopets.core.event;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;

/** Emitted when a player's available skill-point balance changes. */
public record SkillPointsChanged(PlayerId playerId, int previousBalance, int newBalance) implements DomainEvent {
    public SkillPointsChanged {
        Objects.requireNonNull(playerId, "playerId");
        if (previousBalance < 0 || newBalance < 0) {
            throw new IllegalArgumentException("skill-point balances must not be negative");
        }
    }
}
