package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Set;

/** Finds the configured rank identifiers held by a player. */
@FunctionalInterface
public interface RankSource {
    /**
     * Returns rank identifiers held by the player.
     *
     * <p>The returned set may contain identifiers that are not configured; callers must ignore them.
     *
     * @param playerId player whose ranks are queried
     * @param configuredRankIds current non-default configured rank identifiers
     * @return rank identifiers reported by the backing permission system
     */
    Set<String> findRankIds(PlayerId playerId, Set<String> configuredRankIds);
}
