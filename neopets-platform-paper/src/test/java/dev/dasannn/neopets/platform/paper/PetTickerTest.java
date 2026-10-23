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
import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.platform.paper.ability.Ability;
import dev.dasannn.neopets.platform.paper.ability.AbilityContext;
import dev.dasannn.neopets.platform.paper.ability.CooldownTracker;
import dev.dasannn.neopets.platform.paper.ability.DefaultAbilityRegistry;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PetTickerTest {
    @Test
    void sharedTaskTicksActiveAbilitiesAndStopsOnInvalidEntityAndShutdown() {
        var petId = new PetId(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var valid = new AtomicBoolean(true);
        var owner = (Player) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> ownerId;
                    case "isOnline" -> true;
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method);
                });
        var data = (PersistentDataContainer) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "has" -> true;
                    case "get" -> args[0].equals(PetKeys.OWNER) ? ownerId.toString() : petId.toString();
                    default -> throw new AssertionError(method);
                });
        var pathfinder = (Pathfinder) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Pathfinder.class}, (proxy, method, args) -> {
                    if (method.getName().equals("stopPathfinding")) {
                        return null;
                    }
                    throw new AssertionError(method);
                });
        var pet = (Mob) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Mob.class, Sittable.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "isValid" -> valid.get();
                    case "getPersistentDataContainer" -> data;
                    case "getTarget" -> null;
                    case "isSitting" -> true;
                    case "getPathfinder" -> pathfinder;
                    default -> throw new AssertionError(method);
                });
        var server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Server.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getEntity" -> pet;
                    case "getPlayer" -> owner;
                    case "getLogger" -> Logger.getLogger("PetTickerTest");
                    default -> throw new AssertionError(method);
                });
        var calls = new ArrayList<String>();
        var ability = new Ability() {
            @Override
            public String id() {
                return "test";
            }

            @Override
            public void onActivate(LivingEntity entity, Player player, int level) {
                calls.add("activate");
            }

            @Override
            public void onDeactivate(LivingEntity entity, Player player, int level) {
                calls.add("deactivate");
            }

            @Override
            public void tick(AbilityContext context) {
                calls.add("tick");
            }
        };
        var abilities = new DefaultAbilityRegistry(
                List.of(ability), new CooldownTracker(),
                Clock.systemUTC(), ignored -> Optional.of(owner));
        var live = new LivePetRegistry();
        live.register(petId, entityId);
        abilities.update("test", petId, pet, 1, Map.of());
        var scheduler = new RecordingScheduler();
        var ticker = new PetTicker(server, scheduler, live, new PetBrain(4, 16, 1),
                new RecordingGateway(null, null), abilities);
        ticker.start();
        ticker.start();
        scheduler.task.runnable.run();
        valid.set(false);
        scheduler.task.runnable.run();
        scheduler.task.runnable.run();
        assertEquals(List.of("activate", "tick", "deactivate"), calls);
        assertEquals(0, live.size());
        // Shutdown also deactivates still-live abilities before stopping the single task.
        valid.set(true);
        live.register(petId, entityId);
        abilities.update("test", petId, pet, 1, Map.of());
        scheduler.task.runnable.run();
        ticker.close();
        assertEquals(List.of("activate", "tick", "deactivate", "activate", "tick", "deactivate"), calls);
        assertEquals(1, scheduler.scheduledCount);
        assertTrue(scheduler.task.cancelled);
    }

    @Test
    void repeatedStartSchedulesOneTaskAndCloseCancelsIt() {
        var scheduler = new RecordingScheduler();
        var ticker = ticker(scheduler, new LivePetRegistry(), new RecordingGateway(null, null));

        ticker.start();
        ticker.start();

        assertEquals(1, scheduler.scheduledCount);
        assertFalse(scheduler.task.cancelled);
        ticker.close();
        assertTrue(scheduler.task.cancelled);
    }

    @Test
    void unloadReplacesOnlyTheCurrentlyRegisteredEntity() {
        var petId = new PetId(UUID.randomUUID());
        var unloadedEntityId = UUID.randomUUID();
        var replacementEntityId = UUID.randomUUID();
        var registry = new LivePetRegistry();
        registry.register(petId, unloadedEntityId);
        var gateway = new RecordingGateway(registry, replacementEntityId);
        var ticker = ticker(new RecordingScheduler(), registry, gateway);
        var unloaded = taggedEntity(petId, unloadedEntityId);

        ticker.recoverUnloaded(List.of(unloaded));
        ticker.recoverUnloaded(List.of(unloaded));

        assertEquals(1, gateway.spawnCount);
        assertEquals(replacementEntityId, registry.entityId(petId).orElseThrow());
    }

    @Test
    void failedUnloadRecoveryIsRetriedOnTheNextSharedTick() {
        var petId = new PetId(UUID.randomUUID());
        var unloadedEntityId = UUID.randomUUID();
        var replacementEntityId = UUID.randomUUID();
        var registry = new LivePetRegistry();
        registry.register(petId, unloadedEntityId);
        var scheduler = new RecordingScheduler();
        var gateway = new RecordingGateway(registry, replacementEntityId, 1);
        var warnings = new ArrayList<String>();
        var reporter = new RecoveryFailureReporter(
                Duration.ofMinutes(1), () -> 1L, (message, failure) -> warnings.add(message));
        var ticker = ticker(scheduler, registry, gateway, reporter);
        ticker.start();

        ticker.recoverUnloaded(List.of(taggedEntity(petId, unloadedEntityId)));

        assertEquals(1, gateway.spawnCount);
        assertTrue(registry.isRecoveryPending(petId));
        assertEquals(1, warnings.size());

        scheduler.task.runnable.run();

        assertEquals(2, gateway.spawnCount);
        assertEquals(replacementEntityId, registry.entityId(petId).orElseThrow());
        assertFalse(registry.isRecoveryPending(petId));
    }

    @Test
    void explicitDespawnCancelsAQueuedUnloadRecovery() {
        var petId = new PetId(UUID.randomUUID());
        var unloadedEntityId = UUID.randomUUID();
        var registry = new LivePetRegistry();
        registry.register(petId, unloadedEntityId);
        var scheduler = new RecordingScheduler();
        var gateway = new RecordingGateway(registry, UUID.randomUUID(), 1);
        var ticker = ticker(scheduler, registry, gateway);
        ticker.start();

        ticker.recoverUnloaded(List.of(taggedEntity(petId, unloadedEntityId)));
        gateway.despawn(petId).join();
        scheduler.task.runnable.run();

        assertEquals(1, gateway.spawnCount);
        assertFalse(registry.isRecoveryPending(petId));
        assertTrue(registry.entityId(petId).isEmpty());
    }

    @Test
    void transientEntityLookupMissDoesNotForgetActivePet() {
        var petId = new PetId(UUID.randomUUID());
        var entityId = UUID.randomUUID();
        var registry = new LivePetRegistry();
        registry.register(petId, entityId);
        var scheduler = new RecordingScheduler();
        var ticker = ticker(scheduler, registry, new RecordingGateway(null, null));

        ticker.start();
        scheduler.task.runnable.run();

        assertEquals(entityId, registry.entityId(petId).orElseThrow());
    }

    @Test
    void destinationWorldAddReappliesGoalSuppressionAfterPortalTransfer() {
        var follow = goal(VanillaGoal.FOLLOW_OWNER.getNamespacedKey());
        var removed = new ArrayList<Goal<Mob>>();
        var mobGoals = mobGoals(List.of(follow), removed);
        var pet = taggedMob(new PetId(UUID.randomUUID()), mobGoals);
        var world = world();
        var ticker = ticker(new RecordingScheduler(), new LivePetRegistry(), new RecordingGateway(null, null));

        ticker.onEntityAddedToWorld(new EntityAddToWorldEvent(pet, world));

        assertEquals(List.of(follow), removed);
    }

    private static PetTicker ticker(
            RecordingScheduler scheduler,
            LivePetRegistry registry,
            PetEntityGateway gateway
    ) {
        return ticker(scheduler, registry, gateway, null);
    }

    private static PetTicker ticker(
            RecordingScheduler scheduler,
            LivePetRegistry registry,
            PetEntityGateway gateway,
            RecoveryFailureReporter recoveryFailureReporter
    ) {
        var server = (Server) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getEntity" -> null;
                    case "getLogger" -> Logger.getLogger("PetTickerTest");
                    case "toString" -> "test server";
                    default -> throw new AssertionError("Unexpected server call: " + method);
                });
        if (recoveryFailureReporter == null) {
            return new PetTicker(server, scheduler, registry, new PetBrain(4.0, 16.0, 1.0), gateway);
        }
        return new PetTicker(
                server,
                scheduler,
                registry,
                new PetBrain(4.0, 16.0, 1.0),
                gateway,
                PetTicker.DEFAULT_PERIOD_TICKS,
                recoveryFailureReporter);
    }

    private static Entity taggedEntity(PetId petId, UUID entityId) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "get" -> arguments[1] == PersistentDataType.STRING ? petId.toString() : null;
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        return (Entity) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {Entity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "getPersistentDataContainer" -> data;
                    case "toString" -> "test unloaded entity";
                    default -> throw new AssertionError("Unexpected entity call: " + method);
                });
    }

    private static Mob taggedMob(PetId petId, MobGoals mobGoals) {
        var entity = taggedEntity(petId, UUID.randomUUID());
        var server = (Server) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMobGoals" -> mobGoals;
                    case "toString" -> "pet server";
                    default -> throw new AssertionError("Unexpected pet server call: " + method);
                });
        return (Mob) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {Mob.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> entity.getPersistentDataContainer();
                    case "getServer" -> server;
                    case "toString" -> "transferred pet";
                    default -> throw new AssertionError("Unexpected transferred pet call: " + method);
                });
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
                PetTickerTest.class.getClassLoader(),
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

    private static World world() {
        return (World) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {World.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "destination world";
                    }
                    throw new AssertionError("Unexpected world call: " + method);
                });
    }

    private static final class RecordingScheduler implements TaskScheduler {
        private int scheduledCount;
        private RecordedTask task;

        @Override
        public CompletableFuture<Void> runOnMain(Runnable task) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public RepeatingTask runRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
            scheduledCount++;
            task = new RecordedTask(runnable);
            return task;
        }

        @Override
        public boolean isMainThread() {
            return true;
        }

        @Override
        public void quiesce() {
        }
    }

    private static final class RecordedTask implements TaskScheduler.RepeatingTask {
        private final Runnable runnable;
        private boolean cancelled;

        private RecordedTask(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class RecordingGateway implements PetEntityGateway {
        private final LivePetRegistry registry;
        private final UUID replacementEntityId;
        private int failuresRemaining;
        private int spawnCount;

        private RecordingGateway(LivePetRegistry registry, UUID replacementEntityId) {
            this(registry, replacementEntityId, 0);
        }

        private RecordingGateway(LivePetRegistry registry, UUID replacementEntityId, int failuresRemaining) {
            this.registry = registry;
            this.replacementEntityId = replacementEntityId;
            this.failuresRemaining = failuresRemaining;
        }

        @Override
        public CompletableFuture<Void> spawn(PetId petId) {
            spawnCount++;
            if (failuresRemaining > 0) {
                failuresRemaining--;
                return CompletableFuture.failedFuture(new IllegalStateException("spawn failed"));
            }
            if (registry != null) {
                registry.register(petId, replacementEntityId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> despawn(PetId petId) {
            if (registry != null) {
                registry.unregister(petId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> applyEffects(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
