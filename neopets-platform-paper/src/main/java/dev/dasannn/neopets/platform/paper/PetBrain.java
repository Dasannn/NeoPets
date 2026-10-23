package dev.dasannn.neopets.platform.paper;

import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;

/** Runs owner-following behavior for a pet from the shared ticker. */
public final class PetBrain {
    private static final Set<NamespacedKey> CONFLICTING_GOALS = Set.of(
            VanillaGoal.FOLLOW_OWNER.getNamespacedKey(),
            VanillaGoal.RANDOM_STROLL.getNamespacedKey(),
            VanillaGoal.WATER_AVOIDING_RANDOM_STROLL.getNamespacedKey(),
            VanillaGoal.WATER_AVOIDING_RANDOM_FLYING.getNamespacedKey(),
            VanillaGoal.PARROT_WANDER.getNamespacedKey(),
            VanillaGoal.PARROT_LAND_ON_OWNERS_SHOULDER.getNamespacedKey());

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

    /**
     * Gives the shared ticker exclusive control of owner-following movement.
     *
     * <p>The vanilla sitting goal is intentionally retained so an owner can still control sit/stand state.</p>
     */
    public static void suppressConflictingVanillaGoals(Mob pet) {
        Objects.requireNonNull(pet, "pet");
        suppressConflictingVanillaGoals(pet.getServer().getMobGoals(), pet);
    }

    static void suppressConflictingVanillaGoals(MobGoals mobGoals, Mob pet) {
        Objects.requireNonNull(mobGoals, "mobGoals");
        Objects.requireNonNull(pet, "pet");
        var goals = new ArrayList<>(mobGoals.getAllGoals(pet));
        goals.stream()
                .filter(goal -> CONFLICTING_GOALS.contains(goal.getKey().getNamespacedKey()))
                .forEach(goal -> mobGoals.removeGoal(pet, goal));
    }

    /** Performs one follow update; this method must run on the server thread. */
    public void tick(Mob pet, Player owner) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(owner, "owner");

        if (owner.equals(pet.getTarget())) {
            pet.setTarget(null);
            pet.setAggressive(false);
        }

        if (pet instanceof Sittable sittable && sittable.isSitting()) {
            pet.getPathfinder().stopPathfinding();
            return;
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
                if (pet.teleport(owner.getLocation())) {
                    // Paper recreates the underlying mob when changing worlds and restores its vanilla goals.
                    suppressConflictingVanillaGoals(pet);
                    pet.setFallDistance(0.0F);
                }
            }
        }
    }
}
