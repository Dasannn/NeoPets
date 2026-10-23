package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import org.bukkit.Server;
import org.bukkit.entity.Mob;

/** Runs all active pet behavior from one configurable repeating task. */
public final class PetTicker implements AutoCloseable {
    /** Default shared ticker period in server ticks. */
    public static final long DEFAULT_PERIOD_TICKS = 10L;

    private final Server server;
    private final TaskScheduler scheduler;
    private final LivePetRegistry registry;
    private final PetBrain brain;
    private final long periodTicks;
    private TaskScheduler.RepeatingTask task;

    /** Creates a ticker that runs every ten ticks. */
    public PetTicker(Server server, TaskScheduler scheduler, LivePetRegistry registry, PetBrain brain) {
        this(server, scheduler, registry, brain, DEFAULT_PERIOD_TICKS);
    }

    /** Creates a ticker with a configured positive period. */
    public PetTicker(
            Server server,
            TaskScheduler scheduler,
            LivePetRegistry registry,
            PetBrain brain,
            long periodTicks
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.brain = Objects.requireNonNull(brain, "brain");
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
    }

    private void tickAll() {
        for (var entry : registry.snapshot().entrySet()) {
            var entity = server.getEntity(entry.getValue());
            if (!(entity instanceof Mob pet) || !pet.isValid()) {
                registry.unregister(entry.getKey(), entry.getValue());
                continue;
            }
            var ownerId = PetEntityMetadata.ownerId(pet);
            if (ownerId.isEmpty()) {
                registry.unregister(entry.getKey(), entry.getValue());
                pet.remove();
                continue;
            }
            var owner = server.getPlayer(ownerId.orElseThrow());
            if (owner != null && owner.isOnline()) {
                brain.tick(pet, owner);
            }
        }
    }
}
