package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Resolves rank limits by taking each field's maximum across all ranks held by a player. */
public final class ConfiguredRankLimitsResolver implements RankLimitsResolver {
    private static final String DEFAULT_RANK = "default";

    private final Supplier<Map<String, RankLimits>> configuredRanks;
    private final RankSource rankSource;

    /**
     * Creates a resolver backed by the current configured ranks and a platform rank source.
     *
     * <p>The supplier is invoked for every resolution so a successfully published configuration
     * reload takes effect without rebuilding the resolver.
     */
    public ConfiguredRankLimitsResolver(
            Supplier<Map<String, RankLimits>> configuredRanks,
            RankSource rankSource
    ) {
        this.configuredRanks = Objects.requireNonNull(configuredRanks, "configuredRanks");
        this.rankSource = Objects.requireNonNull(rankSource, "rankSource");
    }

    @Override
    public RankLimits resolve(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Map<String, RankLimits> ranks = Map.copyOf(
                Objects.requireNonNull(configuredRanks.get(), "configuredRanks result"));
        RankLimits effective = Objects.requireNonNull(ranks.get(DEFAULT_RANK),
                "configured ranks must contain default");

        Set<String> selectableRanks = ranks.keySet().stream()
                .filter(rankId -> !DEFAULT_RANK.equals(rankId))
                .collect(Collectors.toUnmodifiableSet());
        Set<String> heldRanks = Objects.requireNonNull(
                rankSource.findRankIds(playerId, selectableRanks), "rankSource result");
        for (String rankId : heldRanks) {
            RankLimits limits = ranks.get(rankId);
            if (limits != null) {
                effective = maximum(effective, limits);
            }
        }
        return effective;
    }

    private static RankLimits maximum(RankLimits first, RankLimits second) {
        return new RankLimits(
                Math.max(first.maxActivePets(), second.maxActivePets()),
                Math.max(first.maxSkillPointsPerPet(), second.maxSkillPointsPerPet()),
                Math.max(first.maxNodesPerPet(), second.maxNodesPerPet()));
    }
}
