package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ConfiguredRankLimitsResolverTest {
    private static final PlayerId PLAYER_ID = new PlayerId(
            UUID.fromString("23fc4640-e672-435c-91d6-a4400729c294"));
    private static final RankLimits DEFAULT_LIMITS = new RankLimits(1, 3, 10);

    @Test
    void takesPerFieldMaximumAcrossDefaultAndEveryHeldRank() {
        var ranks = Map.of(
                "default", DEFAULT_LIMITS,
                "vip", new RankLimits(2, 2, 16),
                "builder", new RankLimits(1, 9, 12));
        var resolver = resolver(ranks, ranks("vip", "builder"));

        assertEquals(new RankLimits(2, 9, 16), resolver.resolve(PLAYER_ID));
    }

    @Test
    void appliesDefaultWhenPlayerHasNoConfiguredRanks() {
        var resolver = resolver(
                Map.of("default", DEFAULT_LIMITS, "vip", new RankLimits(2, 6, 16)),
                ranks());

        assertEquals(DEFAULT_LIMITS, resolver.resolve(PLAYER_ID));
    }

    @Test
    void ignoresUnknownRankIdentifiersReportedBySource() {
        var resolver = resolver(
                Map.of("default", DEFAULT_LIMITS, "vip", new RankLimits(2, 6, 16)),
                ranks("moderator", "old_vip"));

        assertEquals(DEFAULT_LIMITS, resolver.resolve(PLAYER_ID));
    }

    @Test
    void usesCurrentLimitsAfterConfigurationReload() {
        var currentRanks = new AtomicReference<>(Map.of(
                "default", DEFAULT_LIMITS,
                "vip", new RankLimits(2, 6, 16)));
        var resolver = new ConfiguredRankLimitsResolver(currentRanks::get, ranks("vip"));

        assertEquals(new RankLimits(2, 6, 16), resolver.resolve(PLAYER_ID));

        currentRanks.set(Map.of(
                "default", new RankLimits(1, 4, 11),
                "vip", new RankLimits(4, 9, 22)));

        assertEquals(new RankLimits(4, 9, 22), resolver.resolve(PLAYER_ID));
    }

    private static ConfiguredRankLimitsResolver resolver(
            Map<String, RankLimits> ranks,
            RankSource rankSource
    ) {
        return new ConfiguredRankLimitsResolver(() -> ranks, rankSource);
    }

    private static RankSource ranks(String... rankIds) {
        Set<String> heldRanks = Set.copyOf(Arrays.asList(rankIds));
        return (ignoredPlayer, ignoredConfiguredRanks) -> heldRanks;
    }
}
