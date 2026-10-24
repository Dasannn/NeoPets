package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.platform.paper.PetEntityMetadata;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/** Converts tagged entity deaths into durable domain deaths and queued soul-item delivery. */
public final class PetDeathListener implements Listener {
    private final PetDeathWorkflow workflow;
    private final LivePetRegistry livePets;
    private final RuntimeWorkTracker work;
    private final Logger logger;

    public PetDeathListener(
            PetDeathWorkflow workflow,
            LivePetRegistry livePets,
            RuntimeWorkTracker work,
            Logger logger
    ) {
        this.workflow = Objects.requireNonNull(workflow, "workflow");
        this.livePets = Objects.requireNonNull(livePets, "livePets");
        this.work = Objects.requireNonNull(work, "work");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Removes vanilla rewards for tagged pets before the final death observation. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRewards(EntityDeathEvent event) {
        if (event.isCancelled() || !PetEntityMetadata.isPet(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    /** Persists the sequenced death workflow after handlers that can cancel the death. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.isCancelled() || !PetEntityMetadata.isPet(event.getEntity())) {
            return;
        }
        var petId = PetEntityMetadata.petId(event.getEntity());
        var ownerId = PetEntityMetadata.ownerId(event.getEntity()).map(PlayerId::new);
        petId.ifPresent(id -> livePets.unregister(id, event.getEntity().getUniqueId()));
        if (petId.isEmpty() || ownerId.isEmpty()) {
            logger.warning("A tagged NeoPets entity died with invalid identity metadata");
            return;
        }
        work.submit(() -> workflow.start(ownerId.orElseThrow(), petId.orElseThrow()))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE, "Could not complete pet death for " + petId.orElseThrow(),
                                unwrap(failure));
                    }
                });
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
