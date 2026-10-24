package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.junit.jupiter.api.Test;

class PetBrainTest {
    @Test
    void removesVanillaFollowWanderAndShoulderGoalsButKeepsSitting() {
        var follow = goal(VanillaGoal.FOLLOW_OWNER.getNamespacedKey());
        var wander = goal(VanillaGoal.WATER_AVOIDING_RANDOM_STROLL.getNamespacedKey());
        var shoulder = goal(VanillaGoal.PARROT_LAND_ON_OWNERS_SHOULDER.getNamespacedKey());
        var sitting = goal(VanillaGoal.SIT_WHEN_ORDERED_TO.getNamespacedKey());
        var removed = new ArrayList<Goal<Mob>>();
        var mobGoals = mobGoals(List.of(follow, wander, shoulder, sitting), removed);
        var pet = pet();

        PetBrain.suppressConflictingVanillaGoals(mobGoals, pet);

        assertEquals(List.of(follow, wander, shoulder), removed);
    }

    @Test
    void sittingPetStopsAndRemainsWhereItsOwnerLeftIt() {
        var world = world("home");
        var state = new PetState(world, new Location(world, 0.0, 0.0, 0.0), false, true, mobGoals(List.of(), new ArrayList<>()));
        var pet = state.pet(true);
        var owner = owner(world, new Location(world, 20.0, 0.0, 0.0));

        new PetBrain(4.0, 16.0, 1.0).tick(pet, owner);

        assertTrue(state.pathfindingStopped);
        assertFalse(state.teleportAttempted);
        assertFalse(state.pathfindingStarted);
    }

    @Test
    void glidingOrFallingOwnerIsFollowedOnFootAndReachedByTeleportAfterLanding() {
        var world = world("home");
        var state = new PetState(world, new Location(world, 0.0, 0.0, 0.0), true, false, mobGoals(List.of(), new ArrayList<>()));
        var pet = state.pet(false);
        var brain = new PetBrain(4.0, 16.0, 1.0);
        var far = new Location(world, 40.0, 80.0, 0.0);

        brain.tick(pet, owner(world, far, true, false));
        brain.tick(pet, owner(world, far, false, false));
        assertFalse(state.teleportAttempted);
        assertTrue(state.pathfindingStarted);

        brain.tick(pet, owner(world, far, false, true));
        assertTrue(state.teleportAttempted);
    }

    @Test
    void ownerAirborneInAnotherWorldIsNotFollowedUntilLanding() {
        var world = world("home");
        var other = world("other");
        var state = new PetState(world, new Location(world, 0.0, 0.0, 0.0), true, false, mobGoals(List.of(), new ArrayList<>()));
        var pet = state.pet(false);

        new PetBrain(4.0, 16.0, 1.0).tick(pet, owner(other, new Location(other, 0.0, 90.0, 0.0), true, false));

        assertFalse(state.teleportAttempted);
        assertTrue(state.pathfindingStopped);
    }

    @Test
    void successfulCrossWorldTeleportReappliesGoalSuppressionToDestinationMob() {
        var sourceWorld = world("source");
        var destinationWorld = world("destination");
        var follow = goal(VanillaGoal.FOLLOW_OWNER.getNamespacedKey());
        var removed = new ArrayList<Goal<Mob>>();
        var state = new PetState(
                sourceWorld,
                new Location(sourceWorld, 0.0, 0.0, 0.0),
                true,
                false,
                mobGoals(List.of(follow), removed));
        var pet = state.pet(false);
        var destination = new Location(destinationWorld, 5.0, 6.0, 7.0);

        new PetBrain(4.0, 16.0, 1.0).tick(pet, owner(destinationWorld, destination));

        assertTrue(state.teleportAttempted);
        assertEquals(destination, state.teleportDestination);
        assertEquals(List.of(follow), removed);
        assertTrue(state.fallDistanceReset);
    }

    @Test
    void failedTeleportDoesNotMutateDestinationOnlyState() {
        var sourceWorld = world("source");
        var destinationWorld = world("destination");
        var mobGoalsRead = new AtomicBoolean();
        var goals = (MobGoals) Proxy.newProxyInstance(
                PetBrainTest.class.getClassLoader(),
                new Class<?>[] {MobGoals.class},
                (proxy, method, arguments) -> {
                    mobGoalsRead.set(true);
                    throw new AssertionError("Goals must not be read after a failed teleport");
                });
        var state = new PetState(
                sourceWorld,
                new Location(sourceWorld, 0.0, 0.0, 0.0),
                false,
                false,
                goals);

        new PetBrain(4.0, 16.0, 1.0).tick(
                state.pet(false), owner(destinationWorld, new Location(destinationWorld, 1.0, 2.0, 3.0)));

        assertTrue(state.teleportAttempted);
        assertFalse(state.fallDistanceReset);
        assertFalse(mobGoalsRead.get());
    }

    private static Goal<Mob> goal(NamespacedKey key) {
        return new Goal<>() {
            @Override
            public boolean shouldActivate() {
                return false;
            }

            @Override
            public GoalKey<Mob> getKey() {
                return GoalKey.of(Mob.class, key);
            }

            @Override
            public EnumSet<GoalType> getTypes() {
                return EnumSet.of(GoalType.MOVE);
            }
        };
    }

    private static MobGoals mobGoals(List<Goal<Mob>> goals, List<Goal<Mob>> removed) {
        return (MobGoals) Proxy.newProxyInstance(
                PetBrainTest.class.getClassLoader(),
                new Class<?>[] {MobGoals.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getAllGoals" -> goals;
                    case "removeGoal" -> {
                        @SuppressWarnings("unchecked")
                        var goal = (Goal<Mob>) arguments[1];
                        removed.add(goal);
                        yield null;
                    }
                    case "toString" -> "test mob goals";
                    default -> throw new AssertionError("Unexpected MobGoals call: " + method);
                });
    }

    private static Mob pet() {
        return (Mob) Proxy.newProxyInstance(
                PetBrainTest.class.getClassLoader(),
                new Class<?>[] {Mob.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "test pet";
                    }
                    throw new AssertionError("Unexpected pet call: " + method);
                });
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(
                PetBrainTest.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError("Unexpected world call: " + method);
                });
    }

    private static Player owner(World world, Location location) {
        return owner(world, location, false, true);
    }

    private static Player owner(World world, Location location, boolean gliding, boolean onGround) {
        return (Player) Proxy.newProxyInstance(
                PetBrainTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getLocation" -> location;
                    case "isGliding" -> gliding;
                    case "isOnGround" -> onGround;
                    case "isFlying", "isInWater", "isClimbing" -> false;
                    case "getVehicle" -> null;
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "test owner";
                    default -> throw new AssertionError("Unexpected owner call: " + method);
                });
    }

    private static final class PetState {
        private final World world;
        private final Location location;
        private final boolean teleportSucceeds;
        private final boolean sitting;
        private final Server server;
        private final Pathfinder pathfinder;
        private boolean pathfindingStopped;
        private boolean pathfindingStarted;
        private boolean teleportAttempted;
        private boolean fallDistanceReset;
        private Location teleportDestination;

        private PetState(
                World world,
                Location location,
                boolean teleportSucceeds,
                boolean sitting,
                MobGoals mobGoals
        ) {
            this.world = world;
            this.location = location;
            this.teleportSucceeds = teleportSucceeds;
            this.sitting = sitting;
            this.server = (Server) Proxy.newProxyInstance(
                    PetBrainTest.class.getClassLoader(),
                    new Class<?>[] {Server.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "getMobGoals" -> mobGoals;
                        case "toString" -> "test server";
                        default -> throw new AssertionError("Unexpected server call: " + method);
                    });
            this.pathfinder = (Pathfinder) Proxy.newProxyInstance(
                    PetBrainTest.class.getClassLoader(),
                    new Class<?>[] {Pathfinder.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "stopPathfinding" -> {
                            pathfindingStopped = true;
                            yield null;
                        }
                        case "moveTo" -> {
                            pathfindingStarted = true;
                            yield true;
                        }
                        case "toString" -> "test pathfinder";
                        default -> throw new AssertionError("Unexpected pathfinder call: " + method);
                    });
        }

        private Mob pet(boolean sittable) {
            var interfaces = sittable ? new Class<?>[] {Mob.class, Sittable.class} : new Class<?>[] {Mob.class};
            return (Mob) Proxy.newProxyInstance(
                    PetBrainTest.class.getClassLoader(), interfaces, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getTarget" -> null;
                        case "getWorld" -> world;
                        case "getLocation" -> location;
                        case "getPathfinder" -> pathfinder;
                        case "isSitting" -> sitting;
                        case "teleport" -> {
                            teleportAttempted = true;
                            teleportDestination = (Location) arguments[0];
                            yield teleportSucceeds;
                        }
                        case "getServer" -> server;
                        case "setFallDistance" -> {
                            fallDistanceReset = true;
                            yield null;
                        }
                        case "toString" -> "test pet";
                        default -> throw new AssertionError("Unexpected pet call: " + method);
                    });
        }
    }
}
