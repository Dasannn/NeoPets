package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;

/** Resolves the effective configured limits for a player. */
@FunctionalInterface
public interface RankLimitsResolver {
    RankLimits resolve(PlayerId playerId);
}
