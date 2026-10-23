package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantAppearance;
import dev.dasannn.neopets.core.model.VariantId;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

class PaperPetEntityGatewayTest {
    @Test
    void cancelledSpawnFailsWithoutRegisteringOrApplyingEffects() {
        var fixture = new GatewayFixture();
        var effectsApplied = new boolean[1];
        var invalidEntity = livingEntity(UUID.randomUUID(), new boolean[] {true});
        var gateway = fixture.gateway(
                new DirectScheduler(),
                (location, ownerId, petId, variant) -> invalidEntity,
                (petId, entity) -> effectsApplied[0] = true);

        var thrown = assertThrows(CompletionException.class, () -> gateway.spawn(fixture.petId).join());

        assertTrue(thrown.getCause().getMessage().contains("cancelled or rejected"));
        assertTrue(fixture.registry.entityId(fixture.petId).isEmpty());
        assertFalse(effectsApplied[0]);
    }

    @Test
    void spawnDefersAllBukkitWorkUntilTheMainThreadTaskRuns() {
        var fixture = new GatewayFixture();
        var scheduler = new DeferredScheduler();
        var spawnCalls = new int[1];
        var entityId = UUID.randomUUID();
        var entity = livingEntity(entityId, new boolean[1]);
        var gateway = fixture.gateway(scheduler, (location, ownerId, petId, variant) -> {
            spawnCalls[0]++;
            return entity;
        }, PetEffectApplier.noOp());

        var result = gateway.spawn(fixture.petId);

        assertFalse(result.isDone());
        assertEquals(0, spawnCalls[0]);

        scheduler.runDeferredTask();

        assertTrue(result.isDone());
        assertFalse(result.isCompletedExceptionally());
        assertEquals(1, spawnCalls[0]);
        assertEquals(entityId, fixture.registry.entityId(fixture.petId).orElseThrow());
    }

    @Test
    void failedEffectInitializationRemovesEntityAndRegistryEntry() {
        var petId = new PetId(UUID.randomUUID());
        var ownerId = UUID.randomUUID();
        var entityId = UUID.randomUUID();
        var removed = new boolean[1];
        var entity = livingEntity(entityId, removed);
        var owner = onlinePlayer(ownerId);
        var server = server(owner);
        var registry = new LivePetRegistry();
        var failure = new IllegalStateException("effects failed");
        var variantId = new VariantId("dog_test");
        var variant = new PetVariant(
                variantId,
                new PetTypeId("dog"),
                VariantAppearance.empty(),
                false,
                1.0,
                "pets.dog_test");
        var gateway = new PaperPetEntityGateway(
                server,
                new DirectScheduler(),
                (location, ignoredOwnerId, ignoredPetId, ignoredVariant) -> entity,
                registry,
                ignored -> new PetSpawnData(ownerId, variantId),
                ignored -> variant,
                (ignoredPetId, ignoredEntity) -> {
                    throw failure;
                });

        var thrown = assertThrows(CompletionException.class, () -> gateway.spawn(petId).join());

        assertSame(failure, thrown.getCause());
        assertTrue(removed[0]);
        assertTrue(registry.entityId(petId).isEmpty());
    }

    @Test
    void despawnClearsMatchingShoulderPetWhenEntityIsAbsentFromWorld() {
        var petId = new PetId(UUID.randomUUID());
        var ownerId = UUID.randomUUID();
        var variantId = new VariantId("parrot_test");
        var registry = new LivePetRegistry();
        registry.register(petId, UUID.randomUUID());
        var shouldersCleared = new boolean[2];
        var owner = playerWithShoulderCopies(ownerId, taggedEntity(petId), shouldersCleared);
        var gateway = new PaperPetEntityGateway(
                server(owner),
                new DirectScheduler(),
                (location, ignoredOwnerId, ignoredPetId, ignoredVariant) -> {
                    throw new AssertionError("despawn must not spawn");
                },
                registry,
                ignored -> new PetSpawnData(ownerId, variantId),
                ignored -> null,
                PetEffectApplier.noOp());

        gateway.despawn(petId).join();

        assertTrue(shouldersCleared[0]);
        assertTrue(shouldersCleared[1]);
        assertTrue(registry.entityId(petId).isEmpty());
    }

    private static final class GatewayFixture {
        private final PetId petId = new PetId(UUID.randomUUID());
        private final UUID ownerId = UUID.randomUUID();
        private final VariantId variantId = new VariantId("dog_test");
        private final PetVariant variant = new PetVariant(
                variantId,
                new PetTypeId("dog"),
                VariantAppearance.empty(),
                false,
                1.0,
                "pets.dog_test");
        private final LivePetRegistry registry = new LivePetRegistry();
        private final Server server = server(onlinePlayer(ownerId));

        private PaperPetEntityGateway gateway(
                TaskScheduler scheduler,
                PaperPetEntityGateway.EntitySpawner spawner,
                PetEffectApplier effectApplier
        ) {
            return new PaperPetEntityGateway(
                    server,
                    scheduler,
                    spawner,
                    registry,
                    ignored -> new PetSpawnData(ownerId, variantId),
                    ignored -> variant,
                    effectApplier);
        }
    }

    private static LivingEntity livingEntity(UUID entityId, boolean[] removed) {
        return (LivingEntity) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    case "isValid" -> !removed[0];
                    case "remove" -> {
                        removed[0] = true;
                        yield null;
                    }
                    case "toString" -> "test living entity";
                    default -> throw new AssertionError("Unexpected entity call: " + method);
                });
    }

    private static Player onlinePlayer(UUID ownerId) {
        return (Player) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> ownerId;
                    case "getLocation" -> new Location(null, 1.0, 2.0, 3.0);
                    case "isOnline" -> true;
                    case "getShoulderEntityLeft", "getShoulderEntityRight" -> null;
                    case "toString" -> "test owner";
                    default -> throw new AssertionError("Unexpected player call: " + method);
                });
    }

    private static Player playerWithShoulderCopies(UUID ownerId, LivingEntity shoulderPet, boolean[] cleared) {
        return (Player) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> ownerId;
                    case "getShoulderEntityLeft" -> cleared[0] ? null : shoulderPet;
                    case "getShoulderEntityRight" -> cleared[1] ? null : shoulderPet;
                    case "setShoulderEntityLeft" -> {
                        assertNull(arguments[0]);
                        cleared[0] = true;
                        yield null;
                    }
                    case "setShoulderEntityRight" -> {
                        assertNull(arguments[0]);
                        cleared[1] = true;
                        yield null;
                    }
                    case "toString" -> "test owner with shoulder pets";
                    default -> throw new AssertionError("Unexpected player call: " + method);
                });
    }

    private static LivingEntity taggedEntity(PetId petId) {
        var data = (PersistentDataContainer) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {PersistentDataContainer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "get" -> arguments[1] == PersistentDataType.STRING ? petId.toString() : null;
                    case "toString" -> "test persistent data";
                    default -> throw new AssertionError("Unexpected PDC call: " + method);
                });
        return (LivingEntity) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPersistentDataContainer" -> data;
                    case "toString" -> "test shoulder pet";
                    default -> throw new AssertionError("Unexpected shoulder pet call: " + method);
                });
    }

    private static Server server(Player owner) {
        return (Server) Proxy.newProxyInstance(
                PaperPetEntityGatewayTest.class.getClassLoader(),
                new Class<?>[] {Server.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getPlayer" -> owner;
                    case "getEntity" -> null;
                    case "toString" -> "test server";
                    default -> throw new AssertionError("Unexpected server call: " + method);
                });
    }

    private static final class DirectScheduler implements TaskScheduler {
        @Override
        public CompletableFuture<Void> runOnMain(Runnable task) {
            var future = new CompletableFuture<Void>();
            try {
                task.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            return future;
        }

        @Override
        public RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMainThread() {
            return true;
        }
    }

    private static final class DeferredScheduler implements TaskScheduler {
        private Runnable deferredTask;
        private CompletableFuture<Void> future;

        @Override
        public CompletableFuture<Void> runOnMain(Runnable task) {
            deferredTask = task;
            future = new CompletableFuture<>();
            return future;
        }

        @Override
        public RepeatingTask runRepeating(Runnable task, long initialDelayTicks, long periodTicks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isMainThread() {
            return false;
        }

        private void runDeferredTask() {
            try {
                deferredTask.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }
    }
}
