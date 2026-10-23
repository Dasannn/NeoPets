package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PetTickerTest {
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

    private static PetTicker ticker(
            RecordingScheduler scheduler,
            LivePetRegistry registry,
            PetEntityGateway gateway
    ) {
        var server = (Server) Proxy.newProxyInstance(
                PetTickerTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getEntity" -> null;
                    case "toString" -> "test server";
                    default -> throw new AssertionError("Unexpected server call: " + method);
                });
        return new PetTicker(server, scheduler, registry, new PetBrain(4.0, 16.0, 1.0), gateway);
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
        private int spawnCount;

        private RecordingGateway(LivePetRegistry registry, UUID replacementEntityId) {
            this.registry = registry;
            this.replacementEntityId = replacementEntityId;
        }

        @Override
        public CompletableFuture<Void> spawn(PetId petId) {
            spawnCount++;
            if (registry != null) {
                registry.register(petId, replacementEntityId);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> despawn(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> applyEffects(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
