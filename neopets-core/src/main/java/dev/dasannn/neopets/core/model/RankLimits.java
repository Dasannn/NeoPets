package dev.dasannn.neopets.core.model;

/** Limits granted to a player by their resolved rank. */
public record RankLimits(int maxActivePets, int maxSkillPointsPerPet, int maxNodesPerPet) {
    public RankLimits {
        if (maxActivePets < 0 || maxSkillPointsPerPet < 0 || maxNodesPerPet < 0) {
            throw new IllegalArgumentException("rank limits must not be negative");
        }
    }
}
