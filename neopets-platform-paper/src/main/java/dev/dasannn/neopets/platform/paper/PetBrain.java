package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Runs owner-following behavior for a pet from the shared ticker. */
public final class PetBrain {
    private final FollowPolicy followPolicy;
    private final double pathfindingSpeed;

    /** Creates a follow brain with configurable block thresholds and pathfinding speed multiplier. */
    public PetBrain(double followDistance, double teleportDistance, double pathfindingSpeed) {
        this.followPolicy = new FollowPolicy(followDistance, teleportDistance);
        if (!Double.isFinite(pathfindingSpeed) || pathfindingSpeed <= 0.0) {
            throw new IllegalArgumentException("pathfindingSpeed must be finite and greater than zero");
        }
        this.pathfindingSpeed = pathfindingSpeed;
    }

    /** Performs one follow update; this method must run on the server thread. */
    public void tick(Mob pet, Player owner) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(owner, "owner");

        if (owner.equals(pet.getTarget())) {
            pet.setTarget(null);
            pet.setAggressive(false);
        }

        var sameWorld = pet.getWorld().equals(owner.getWorld());
        var distanceSquared = sameWorld
                ? pet.getLocation().distanceSquared(owner.getLocation())
                : 0.0;
        switch (followPolicy.choose(sameWorld, distanceSquared)) {
            case IDLE -> pet.getPathfinder().stopPathfinding();
            case PATHFIND -> pet.getPathfinder().moveTo(owner, pathfindingSpeed);
            case TELEPORT -> {
                pet.getPathfinder().stopPathfinding();
                pet.teleport(owner.getLocation());
                pet.setFallDistance(0.0F);
            }
        }
    }
}
