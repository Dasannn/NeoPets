package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import net.luckperms.api.LuckPerms;

final class LuckPermsRankSource implements RankSource {
    private static final String RANK_PERMISSION_PREFIX = "neopets.rank.";

    private final LuckPerms luckPerms;

    LuckPermsRankSource(LuckPerms luckPerms) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
    }

    @Override
    public Set<String> findRankIds(PlayerId playerId, Set<String> configuredRankIds) {
        var user = luckPerms.getUserManager().getUser(playerId.value());
        if (user == null) {
            return Set.of();
        }

        var queryOptions = user.getQueryOptions();
        var heldRanks = new HashSet<String>();
        user.getInheritedGroups(queryOptions).stream()
                .map(group -> group.getName())
                .forEach(heldRanks::add);

        var permissions = user.getCachedData().getPermissionData(queryOptions);
        configuredRankIds.stream()
                .filter(rankId -> permissions.checkPermission(RANK_PERMISSION_PREFIX + rankId).asBoolean())
                .forEach(heldRanks::add);
        return Set.copyOf(heldRanks);
    }
}
