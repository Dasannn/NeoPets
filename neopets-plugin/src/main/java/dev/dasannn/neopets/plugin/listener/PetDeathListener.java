package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.DeathResult;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.platform.paper.PetEntityMetadata;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
    private final PetService pets;
    private final SkillTreeService skillTrees;
    private final SoulItemService souls;
    private final SoulDeliveryCoordinator deliveries;
    private final LivePetRegistry livePets;
    private final ConfigurationManager configuration;
    private final RuntimeWorkTracker work;
    private final Logger logger;

    public PetDeathListener(
            PetService pets,
            SkillTreeService skillTrees,
            SoulItemService souls,
            SoulDeliveryCoordinator deliveries,
            LivePetRegistry livePets,
            ConfigurationManager configuration,
            RuntimeWorkTracker work,
            Logger logger
    ) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.skillTrees = Objects.requireNonNull(skillTrees, "skillTrees");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.livePets = Objects.requireNonNull(livePets, "livePets");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.work = Objects.requireNonNull(work, "work");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Removes vanilla rewards immediately, then persists the sequenced death workflow asynchronously. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        if (!PetEntityMetadata.isPet(event.getEntity())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        var petId = PetEntityMetadata.petId(event.getEntity());
        var ownerId = PetEntityMetadata.ownerId(event.getEntity()).map(PlayerId::new);
        petId.ifPresent(id -> livePets.unregister(id, event.getEntity().getUniqueId()));
        if (petId.isEmpty() || ownerId.isEmpty()) {
            logger.warning("A tagged NeoPets entity died with invalid identity metadata");
            return;
        }
        boolean keepSkills = configuration.current().settings().features().death().keepSkills();
        work.submit(() -> process(ownerId.orElseThrow(), petId.orElseThrow(), keepSkills))
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        logger.log(Level.SEVERE, "Could not complete pet death for " + petId.orElseThrow(),
                                unwrap(failure));
                    }
                });
    }

    private CompletableFuture<Void> process(
            PlayerId ownerId, dev.dasannn.neopets.core.model.PetId petId, boolean keepSkills
    ) {
        return pets.die(ownerId, petId).thenCompose(result -> {
            if (!(result instanceof DeathResult.Died died)) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> reset = keepSkills
                    ? CompletableFuture.completedFuture(null)
                    : resetSkills(ownerId, petId);
            return reset.thenCompose(ignored -> souls.issue(died.pet().id(), died.pet().owner()))
                    .thenCompose(deliveries::enqueueAndDeliver);
        });
    }

    private CompletableFuture<Void> resetSkills(
            PlayerId ownerId, dev.dasannn.neopets.core.model.PetId petId
    ) {
        return skillTrees.reset(ownerId, petId, true).thenCompose(result -> switch (result) {
            case ResetResult.Reset ignored -> CompletableFuture.completedFuture(null);
            case ResetResult.NotOwned notOwned -> CompletableFuture.failedFuture(
                    new IllegalStateException("Pet became unowned during death: " + notOwned.petId()));
            case ResetResult.Failed failed -> CompletableFuture.failedFuture(failed.cause());
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
