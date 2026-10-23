package dev.dasannn.neopets.platform.paper;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.platform.paper.effect.AbilityRegistry;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;

/** Runs all active pet behavior from one configurable repeating task. */
public final class PetTicker implements AutoCloseable, Listener {
    /** Default shared ticker period in server ticks. */
    public static final long DEFAULT_PERIOD_TICKS = 10L;

    private final Server server;
    private final TaskScheduler scheduler;
    private final LivePetRegistry registry;
    private final PetBrain brain;
    private final PetEntityGateway entityGateway;
    private final RecoveryFailureReporter recoveryFailureReporter;
    private final AbilityRegistry abilities;
    private final long periodTicks;
    private final Set<PetId> recoveriesInFlight = ConcurrentHashMap.newKeySet();
    private TaskScheduler.RepeatingTask task;

    /** Creates a ticker that runs every ten ticks. */
    public PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            PetEntityGateway entityGateway
    ) {
        this(server, scheduler, registry, brain, entityGateway, DEFAULT_PERIOD_TICKS,
                new RecoveryFailureReporter(server.getLogger()), AbilityRegistry.empty());
    }

    /** Creates a ticker with a configured positive period. */
    public PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            PetEntityGateway entityGateway,
            long periodTicks
    ) {
        this(server, scheduler, registry, brain, entityGateway, periodTicks,
                new RecoveryFailureReporter(server.getLogger()), AbilityRegistry.empty());
    }

    /** Creates a ticker that also drives custom abilities from the same shared task. */
    public PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            PetEntityGateway entityGateway,
            AbilityRegistry abilities
    ) {
        this(server, scheduler, registry, brain, entityGateway, DEFAULT_PERIOD_TICKS,
                new RecoveryFailureReporter(server.getLogger()), abilities);
    }

    PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            PetEntityGateway entityGateway,
            long periodTicks,
            RecoveryFailureReporter recoveryFailureReporter
    ) {
        this(server, scheduler, registry, brain, entityGateway, periodTicks,
                recoveryFailureReporter, AbilityRegistry.empty());
    }

    PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            PetEntityGateway entityGateway,
            long periodTicks,
            RecoveryFailureReporter recoveryFailureReporter,
            AbilityRegistry abilities
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.brain = Objects.requireNonNull(brain, "brain");
        this.entityGateway = Objects.requireNonNull(entityGateway, "entityGateway");
        this.recoveryFailureReporter = Objects.requireNonNull(recoveryFailureReporter, "recoveryFailureReporter");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        if (periodTicks <= 0) {
            throw new IllegalArgumentException("periodTicks must be greater than zero");
        }
        this.periodTicks = periodTicks;
    }

    /** Starts the sole repeating task; repeated calls while running have no effect. */
    public synchronized void start() {
        if (task != null && !task.isCancelled()) {
            return;
        }
        task = scheduler.runRepeating(this::tickAll, periodTicks, periodTicks);
    }

    /** Cancels the shared task if running. */
    @Override
    public synchronized void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        abilities.deactivateAll();
    }

    /** Recreates active non-persistent pets when Paper unloads their chunk. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        recoverUnloaded(event.getEntities());
    }

    /** Reapplies custom movement control after Paper recreates a pet during a world or portal transfer. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityAddedToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof Mob pet && PetEntityMetadata.petId(pet).isPresent()) {
            PetBrain.suppressConflictingVanillaGoals(pet);
        }
    }

    void recoverUnloaded(Collection<? extends Entity> entities) {
        for (var entity : entities) {
            PetEntityMetadata.petId(entity).ifPresent(petId -> {
                if (registry.beginRecovery(petId, entity.getUniqueId())) {
                    requestRecovery(petId);
                }
            });
        }
    }

    private void tickAll() {
        abilities.maintain();
        registry.pendingRecoveriesSnapshot().forEach(this::requestRecovery);
        for (var entry : registry.snapshot().entrySet()) {
            var entity = server.getEntity(entry.getValue());
            if (entity == null) {
                // EntitiesUnloadEvent performs recovery. Retain the association until that event is observed so a
                // transient lookup miss cannot permanently forget an otherwise active domain pet.
                continue;
            }
            if (!(entity instanceof Mob pet) || !pet.isValid()) {
                abilities.deactivate(entry.getKey());
                registry.unregister(entry.getKey(), entry.getValue());
                continue;
            }
            var ownerId = PetEntityMetadata.ownerId(pet);
            if (ownerId.isEmpty()) {
                abilities.deactivate(entry.getKey());
                registry.unregister(entry.getKey(), entry.getValue());
                pet.remove();
                continue;
            }
            var owner = server.getPlayer(ownerId.orElseThrow());
            if (owner != null && owner.isOnline()) {
                if (!pet.getPersistentDataContainer().has(PetKeys.COURIER_RECIPIENT, PersistentDataType.STRING)) {
                    brain.tick(pet, owner);
                }
                abilities.tick(entry.getKey(), pet, owner);
            }
        }
    }

    private void requestRecovery(PetId petId) {
        if (!registry.isRecoveryPending(petId) || !recoveriesInFlight.add(petId)) {
            return;
        }
        try {
            var recovery = Objects.requireNonNull(entityGateway.spawn(petId), "entityGateway.spawn result");
            recovery.whenComplete((ignored, failure) -> {
                recoveriesInFlight.remove(petId);
                if (failure == null) {
                    recoveryFailureReporter.recovered(petId);
                } else {
                    recoveryFailureReporter.report(petId, failure);
                }
            });
        } catch (RuntimeException failure) {
            recoveriesInFlight.remove(petId);
            recoveryFailureReporter.report(petId, failure);
        }
    }
}
