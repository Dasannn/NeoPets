package dev.dasannn.neopets.platform.paper;

/** Pure distance policy used by {@link PetBrain}. */
public final class FollowPolicy {
    private final double followDistanceSquared;
    private final double teleportDistanceSquared;

    /**
     * Creates a policy whose thresholds are measured in blocks.
     *
     * @throws IllegalArgumentException when values are non-finite, negative, or teleport is not farther than follow
     */
    public FollowPolicy(double followDistance, double teleportDistance) {
        if (!Double.isFinite(followDistance) || followDistance < 0.0) {
            throw new IllegalArgumentException("followDistance must be finite and not negative");
        }
        if (!Double.isFinite(teleportDistance) || teleportDistance <= followDistance) {
            throw new IllegalArgumentException("teleportDistance must be finite and greater than followDistance");
        }
        this.followDistanceSquared = followDistance * followDistance;
        this.teleportDistanceSquared = teleportDistance * teleportDistance;
    }

    /** Selects the movement action without accessing Bukkit state. */
    public FollowAction choose(boolean sameWorld, double distanceSquared) {
        if (!sameWorld) {
            return FollowAction.TELEPORT;
        }
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
            throw new IllegalArgumentException("distanceSquared must be finite and not negative");
        }
        if (distanceSquared > teleportDistanceSquared) {
            return FollowAction.TELEPORT;
        }
        if (distanceSquared > followDistanceSquared) {
            return FollowAction.PATHFIND;
        }
        return FollowAction.IDLE;
    }
}
