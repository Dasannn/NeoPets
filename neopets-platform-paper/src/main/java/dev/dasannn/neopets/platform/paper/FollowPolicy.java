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

    /**
     * Selects the movement action without accessing Bukkit state. While the owner is airborne
     * (gliding, flying or falling) the pet never teleports, so it cannot inherit a lethal fall;
     * it keeps walking toward the owner and teleports once the owner has landed.
     */
    public FollowAction choose(boolean sameWorld, double distanceSquared, boolean ownerGrounded) {
        if (!sameWorld) {
            return ownerGrounded ? FollowAction.TELEPORT : FollowAction.IDLE;
        }
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
            throw new IllegalArgumentException("distanceSquared must be finite and not negative");
        }
        if (distanceSquared > teleportDistanceSquared) {
            return ownerGrounded ? FollowAction.TELEPORT : FollowAction.PATHFIND;
        }
        if (distanceSquared > followDistanceSquared) {
            return FollowAction.PATHFIND;
        }
        return FollowAction.IDLE;
    }
}
