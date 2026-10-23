package dev.dasannn.neopets.platform.paper;

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
import org.junit.jupiter.api.Test;

class PaperPetEntityGatewayTest {
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
                    case "toString" -> "test owner";
                    default -> throw new AssertionError("Unexpected player call: " + method);
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
}
