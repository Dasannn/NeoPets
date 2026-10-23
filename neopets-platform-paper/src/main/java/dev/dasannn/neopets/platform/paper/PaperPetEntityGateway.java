package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Paper entity lifecycle adapter that is safe to call from any thread.
 *
 * <p>Domain lookups are intentionally small synchronous functions over already-loaded data. All Bukkit access is
 * dispatched through {@link TaskScheduler}.</p>
 */
public final class PaperPetEntityGateway implements PetEntityGateway {
    private final Server server;
    private final TaskScheduler scheduler;
    private final EntitySpawner spawner;
    private final LivePetRegistry registry;
    private final Function<PetId, PetSpawnData> spawnDataLookup;
    private final Function<VariantId, PetVariant> variantLookup;
    private final PetEffectApplier effectApplier;

    /** Creates a gateway with the no-op effect applier used before skill-tree wiring is available. */
    public PaperPetEntityGateway(
            Server server,
            TaskScheduler scheduler,
            PetSpawner spawner,
            LivePetRegistry registry,
            Function<PetId, PetSpawnData> spawnDataLookup,
            Function<VariantId, PetVariant> variantLookup
    ) {
        this(server, scheduler, spawner, registry, spawnDataLookup, variantLookup, PetEffectApplier.noOp());
    }

    /** Creates a gateway with explicit cached-data lookups and effect application. */
    public PaperPetEntityGateway(
            Server server,
            TaskScheduler scheduler,
            PetSpawner spawner,
            LivePetRegistry registry,
            Function<PetId, PetSpawnData> spawnDataLookup,
            Function<VariantId, PetVariant> variantLookup,
            PetEffectApplier effectApplier
    ) {
        this(
                server,
                scheduler,
                Objects.requireNonNull(spawner, "spawner")::spawn,
                registry,
                spawnDataLookup,
                variantLookup,
                effectApplier);
    }

    PaperPetEntityGateway(
            Server server,
            TaskScheduler scheduler,
            EntitySpawner spawner,
            LivePetRegistry registry,
            Function<PetId, PetSpawnData> spawnDataLookup,
            Function<VariantId, PetVariant> variantLookup,
            PetEffectApplier effectApplier
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.spawner = Objects.requireNonNull(spawner, "spawner");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.spawnDataLookup = Objects.requireNonNull(spawnDataLookup, "spawnDataLookup");
        this.variantLookup = Objects.requireNonNull(variantLookup, "variantLookup");
        this.effectApplier = Objects.requireNonNull(effectApplier, "effectApplier");
    }

    @Override
    public CompletableFuture<Void> spawn(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return scheduler.runOnMain(() -> spawnOnMain(petId));
    }

    @Override
    public CompletableFuture<Void> despawn(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return scheduler.runOnMain(() -> despawnOnMain(petId));
    }

    @Override
    public CompletableFuture<Void> applyEffects(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return scheduler.runOnMain(() -> registry.entityId(petId)
                .map(server::getEntity)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(LivingEntity::isValid)
                .ifPresent(entity -> effectApplier.apply(petId, entity)));
    }

    private void spawnOnMain(PetId petId) {
        var existingId = registry.entityId(petId).orElse(null);
        if (existingId != null) {
            var existing = server.getEntity(existingId);
            if (existing != null && existing.isValid()) {
                return;
            }
            registry.unregister(petId, existingId);
        }

        var spawnData = Objects.requireNonNull(spawnDataLookup.apply(petId),
                () -> "No spawn data for pet " + petId);
        var variant = Objects.requireNonNull(variantLookup.apply(spawnData.variantId()),
                () -> "Unknown pet variant " + spawnData.variantId());
        var owner = server.getPlayer(spawnData.ownerId());
        if (owner == null || !owner.isOnline()) {
            throw new IllegalStateException("Pet owner is not online: " + spawnData.ownerId());
        }
        clearShoulderedPet(owner, petId);

        var entity = spawner.spawn(owner.getLocation(), spawnData.ownerId(), petId, variant);
        if (entity == null || !entity.isValid()) {
            throw new IllegalStateException(
                    "NeoPets pet spawn was cancelled or rejected by the server, possibly by another plugin: " + petId);
        }
        try {
            if (entity instanceof LivingEntity livingEntity) {
                effectApplier.apply(petId, livingEntity);
            }
            registry.register(petId, entity.getUniqueId());
        } catch (Throwable failure) {
            try {
                effectApplier.remove(petId);
                entity.remove();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private void despawnOnMain(PetId petId) {
        var entityId = registry.unregister(petId);
        effectApplier.remove(petId);
        entityId
                .map(server::getEntity)
                .ifPresent(entity -> {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                });

        var spawnData = spawnDataLookup.apply(petId);
        if (spawnData == null) {
            return;
        }
        var owner = server.getPlayer(spawnData.ownerId());
        if (owner != null) {
            clearShoulderedPet(owner, petId);
        }
    }

    @SuppressWarnings("deprecation") // Paper exposes no non-releasing API for inspecting serialized shoulder data.
    private static void clearShoulderedPet(Player owner, PetId petId) {
        if (isPet(owner.getShoulderEntityLeft(), petId)) {
            owner.setShoulderEntityLeft(null);
        }
        if (isPet(owner.getShoulderEntityRight(), petId)) {
            owner.setShoulderEntityRight(null);
        }
    }

    private static boolean isPet(Entity entity, PetId petId) {
        return entity != null && PetEntityMetadata.petId(entity).filter(petId::equals).isPresent();
    }

    @FunctionalInterface
    interface EntitySpawner {
        Entity spawn(Location location, UUID ownerId, PetId petId, PetVariant variant);
    }
}
