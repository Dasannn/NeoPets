package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.DeathResult;
import dev.dasannn.neopets.core.pet.PetDeathWork;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PetDeathWorkRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Runs and reconciles the durable, idempotent steps caused by a pet death. */
public final class PetDeathWorkflow {
    @FunctionalInterface
    interface DeathTransition {
        CompletableFuture<DeathResult> die(PlayerId ownerId, PetId petId);
    }

    @FunctionalInterface
    interface SkillReset {
        CompletableFuture<ResetResult> reset(PlayerId ownerId, PetId petId);
    }

    @FunctionalInterface
    interface SoulIssuer {
        CompletableFuture<SoulToken> issue(PetId petId, PlayerId ownerId);
    }

    @FunctionalInterface
    interface SoulEnqueuer {
        CompletableFuture<Void> enqueue(SoulToken token);
    }

    private final PetRepository pets;
    private final PetDeathWorkRepository work;
    private final DeathTransition deaths;
    private final SkillReset skills;
    private final SoulIssuer souls;
    private final SoulEnqueuer deliveries;
    private final BooleanSupplier keepSkills;
    private final Clock clock;

    /** Creates a workflow backed by the production domain services. */
    public PetDeathWorkflow(
            PetRepository pets,
            PetDeathWorkRepository work,
            PetService petService,
            SkillTreeService skillTrees,
            SoulItemService souls,
            SoulDeliveryCoordinator deliveries,
            BooleanSupplier keepSkills,
            Clock clock
    ) {
        this(pets, work, petService::die,
                (ownerId, petId) -> skillTrees.reset(ownerId, petId, true),
                souls::issue, deliveries::enqueueAndDeliver, keepSkills, clock);
    }

    PetDeathWorkflow(
            PetRepository pets,
            PetDeathWorkRepository work,
            DeathTransition deaths,
            SkillReset skills,
            SoulIssuer souls,
            SoulEnqueuer deliveries,
            BooleanSupplier keepSkills,
            Clock clock
    ) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.work = Objects.requireNonNull(work, "work");
        this.deaths = Objects.requireNonNull(deaths, "deaths");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.souls = Objects.requireNonNull(souls, "souls");
        this.deliveries = Objects.requireNonNull(deliveries, "deliveries");
        this.keepSkills = Objects.requireNonNull(keepSkills, "keepSkills");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Records the configured policy before transitioning the pet, then completes every remaining step. */
    public CompletableFuture<Void> start(PlayerId ownerId, PetId petId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(petId, "petId");
        return pets.findById(petId).thenCompose(found -> {
            if (found.isEmpty() || !found.orElseThrow().owner().equals(ownerId)) {
                return CompletableFuture.completedFuture(null);
            }
            OwnedPet pet = found.orElseThrow();
            if (pet.status() == PetStatus.DEAD) {
                return ensureWork(pet).thenCompose(this::complete);
            }
            PetDeathWork started = new PetDeathWork(
                    petId, ownerId, keepSkills.getAsBoolean(), false, clock.instant());
            return work.save(started).thenCompose(ignored -> deaths.die(ownerId, petId))
                    .thenCompose(result -> switch (result) {
                        case DeathResult.Died ignored -> complete(started);
                        case DeathResult.AlreadyDead ignored -> complete(started);
                        case DeathResult.NotOwned ignored -> CompletableFuture.completedFuture(null);
                        case DeathResult.InTransit ignored -> CompletableFuture.completedFuture(null);
                    });
        });
    }

    /** Completes interrupted death workflows for every dead pet owned by a joining player. */
    public CompletableFuture<Void> reconcile(PlayerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return pets.findByOwner(ownerId).thenCompose(owned -> reconcileInOrder(
                owned.stream().filter(pet -> pet.status() == PetStatus.DEAD).toList(), 0));
    }

    private CompletableFuture<Void> reconcileInOrder(List<OwnedPet> deadPets, int index) {
        if (index == deadPets.size()) {
            return CompletableFuture.completedFuture(null);
        }
        return ensureWork(deadPets.get(index)).thenCompose(this::complete)
                .thenCompose(ignored -> reconcileInOrder(deadPets, index + 1));
    }

    private CompletableFuture<PetDeathWork> ensureWork(OwnedPet pet) {
        return work.findByPet(pet.id()).thenCompose(found -> {
            if (found.isPresent()) {
                PetDeathWork existing = found.orElseThrow();
                if (!existing.ownerId().equals(pet.owner())) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "death workflow belongs to a different owner"));
                }
                return CompletableFuture.completedFuture(existing);
            }
            PetDeathWork recovered = new PetDeathWork(
                    pet.id(), pet.owner(), keepSkills.getAsBoolean(), false, clock.instant());
            return work.save(recovered).thenApply(ignored -> recovered);
        });
    }

    private CompletableFuture<Void> complete(PetDeathWork deathWork) {
        CompletableFuture<PetDeathWork> handled;
        if (deathWork.skillsHandled()) {
            handled = CompletableFuture.completedFuture(deathWork);
        } else if (deathWork.keepSkills()) {
            handled = checkpointSkills(deathWork);
        } else {
            handled = skills.reset(deathWork.ownerId(), deathWork.petId()).thenCompose(result -> switch (result) {
                case ResetResult.Reset ignored -> checkpointSkills(deathWork);
                case ResetResult.NotOwned notOwned -> CompletableFuture.failedFuture(
                        new IllegalStateException("Pet became unowned during death: " + notOwned.petId()));
                case ResetResult.Failed failed -> CompletableFuture.failedFuture(failed.cause());
            });
        }
        return handled.thenCompose(current -> souls.issue(current.petId(), current.ownerId()))
                .thenCompose(deliveries::enqueue);
    }

    private CompletableFuture<PetDeathWork> checkpointSkills(PetDeathWork deathWork) {
        PetDeathWork handled = deathWork.withSkillsHandled();
        return work.save(handled).thenApply(ignored -> handled);
    }
}
