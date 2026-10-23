package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
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
}
