package dev.dasannn.neopets.core.courier;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Serializes the request, accepted-delivery, and return lifecycle of courier donkeys. */
public final class CourierService {
    private final PetRepository pets;
    private final PetEntityGateway entities;
    private final PlayerOperationQueue operations;
    private final Clock clock;
    private final Supplier<Duration> timeout;
    private final Supplier<Duration> cooldown;
    private final Map<PetId, CourierRequest> requests = new HashMap<>();
    private final Map<PetId, Instant> nextSend = new HashMap<>();

    public CourierService(
            PetRepository pets,
            PetEntityGateway entities,
            PlayerOperationQueue operations,
            Clock clock,
            Supplier<Duration> timeout,
            Supplier<Duration> cooldown
    ) {
        this.pets = Objects.requireNonNull(pets, "pets");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /** Starts a request after durably marking the pet in transit and removing its owner-side entity. */
    public CompletableFuture<CourierResult> request(PetId petId, PlayerId owner, PlayerId recipient) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(recipient, "recipient");
        if (owner.equals(recipient)) throw new IllegalArgumentException("recipient must differ from owner");
        return operations.submit(owner, () -> pets.findById(petId).thenCompose(found -> {
            if (found.isEmpty() || !found.orElseThrow().owner().equals(owner)) {
                return completed(new CourierResult.NotOwned(petId));
            }
            OwnedPet pet = found.orElseThrow();
            Instant now = clock.instant();
            synchronized (this) {
                CourierRequest current = requests.get(petId);
                if (pet.status() != PetStatus.ALIVE || current != null) {
                    return completed(new CourierResult.Unavailable(petId));
                }
                Instant availableAt = nextSend.get(petId);
                if (availableAt != null && now.isBefore(availableAt)) {
                    return completed(new CourierResult.CoolingDown(Duration.between(now, availableAt)));
                }
            }
            Duration requestTimeout = requirePositive(timeout.get(), "request timeout");
            Duration sendCooldown = requireNonNegative(cooldown.get(), "courier cooldown");
            CourierRequest request = new CourierRequest(UUID.randomUUID(), petId, owner, recipient,
                    now.plus(requestTimeout), false, pet.active());
            OwnedPet travelling = pet.withStatus(PetStatus.IN_TRANSIT);
            return pets.save(travelling).thenCompose(ignored -> entities.despawn(petId)
                    .exceptionallyCompose(failure -> pets.save(pet)
                            .thenCompose(restored -> pet.active() ? entities.spawn(petId)
                                    : CompletableFuture.completedFuture(null))
                            .handle((restored, recoveryFailure) -> {
                                if (recoveryFailure != null) failure.addSuppressed(recoveryFailure);
                                throw new java.util.concurrent.CompletionException(failure);
                            })))
                    .thenApply(ignored -> {
                        synchronized (this) {
                            requests.put(petId, request);
                            nextSend.put(petId, now.plus(sendCooldown));
                        }
                        return (CourierResult) new CourierResult.Requested(request);
                    });
        }));
    }

    /** Accepts an addressed request and spawns its invulnerable entity next to the recipient. */
    public CompletableFuture<CourierResult> accept(UUID requestId, PlayerId recipient) {
        return withRequest(requestId, recipient, request -> {
            if (request.accepted()) return completed(new CourierResult.AlreadyAccepted(request));
            if (!clock.instant().isBefore(request.expiresAt())) {
                return returnLocked(request, CourierResult.Reason.TIMED_OUT);
            }
            CourierRequest accepted = request.accept();
            return entities.spawnForCourier(request.petId(), recipient).thenApply(ignored -> {
                synchronized (this) { requests.put(request.petId(), accepted); }
                return (CourierResult) new CourierResult.Accepted(accepted);
            });
        });
    }

    /** Rejects an addressed request and returns the donkey. */
    public CompletableFuture<CourierResult> reject(UUID requestId, PlayerId recipient) {
        return withRequest(requestId, recipient, request -> request.accepted()
                ? completed(new CourierResult.AlreadyAccepted(request))
                : returnLocked(request, CourierResult.Reason.REJECTED));
    }

    /** Returns an accepted delivery at the recipient's request. */
    public CompletableFuture<CourierResult> returnToOwner(PetId petId, PlayerId recipient) {
        CourierRequest request;
        synchronized (this) { request = requests.get(petId); }
        if (request == null) return completed(new CourierResult.UnknownRequest());
        if (!request.recipient().equals(recipient)) return completed(new CourierResult.WrongRecipient());
        if (!request.accepted()) return completed(new CourierResult.UnknownRequest());
        return operations.submit(request.owner(), () -> returnLocked(request, CourierResult.Reason.RECIPIENT_RETURNED));
    }

    /** Returns all trips addressed to a player, for quit and world-change handling. */
    public CompletableFuture<List<CourierResult.Returned>> recipientUnavailable(
            PlayerId recipient, CourierResult.Reason reason
    ) {
        Objects.requireNonNull(recipient, "recipient");
        if (reason != CourierResult.Reason.RECIPIENT_QUIT
                && reason != CourierResult.Reason.RECIPIENT_WORLD_CHANGE) {
            throw new IllegalArgumentException("reason must describe recipient availability");
        }
        List<CourierRequest> matching;
        synchronized (this) {
            matching = requests.values().stream().filter(r -> r.recipient().equals(recipient)).toList();
        }
        var returned = new ArrayList<CompletableFuture<CourierResult>>();
        for (CourierRequest request : matching) {
            returned.add(operations.submit(request.owner(), () -> returnLocked(request, reason)));
        }
        return CompletableFuture.allOf(returned.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> returned.stream().map(CompletableFuture::join)
                        .filter(CourierResult.Returned.class::isInstance)
                        .map(CourierResult.Returned.class::cast).toList());
    }

    /** Returns every request whose deadline has elapsed. */
    public CompletableFuture<List<CourierResult.Returned>> expire() {
        Instant now = clock.instant();
        List<CourierRequest> expired;
        synchronized (this) {
            expired = requests.values().stream().filter(r -> !now.isBefore(r.expiresAt())).toList();
        }
        var returned = expired.stream()
                .map(request -> operations.submit(request.owner(),
                        () -> returnLocked(request, CourierResult.Reason.TIMED_OUT)))
                .toList();
        return CompletableFuture.allOf(returned.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> returned.stream().map(CompletableFuture::join)
                        .filter(CourierResult.Returned.class::isInstance)
                        .map(CourierResult.Returned.class::cast).toList());
    }

    /** Returns an owner's trips before their cached spawn and inventory data are unloaded. */
    public CompletableFuture<Void> ownerUnavailable(PlayerId owner) {
        Objects.requireNonNull(owner, "owner");
        return operations.submit(owner, () -> {
            List<CourierRequest> owned;
            synchronized (this) {
                owned = requests.values().stream().filter(request -> request.owner().equals(owner)).toList();
            }
            CompletableFuture<Void> returned = CompletableFuture.completedFuture(null);
            for (CourierRequest request : owned) {
                returned = returned.thenCompose(ignored -> returnLocked(request, CourierResult.Reason.OWNER_QUIT)
                        .thenApply(result -> null));
            }
            return returned;
        });
    }

    /** Restores persisted trips left without a live request after a process restart. */
    public CompletableFuture<Void> recover(PlayerId owner) {
        Objects.requireNonNull(owner, "owner");
        return operations.submit(owner, () -> pets.findByOwner(owner).thenCompose(owned -> {
            var saves = owned.stream().filter(pet -> pet.status() == PetStatus.IN_TRANSIT)
                    .filter(pet -> requestFor(pet.id()).isEmpty())
                    .map(pet -> pets.save(pet.withStatus(PetStatus.ALIVE)))
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(saves);
        }));
    }

    /** Finds a pet's active request. */
    public synchronized Optional<CourierRequest> requestFor(PetId petId) {
        return Optional.ofNullable(requests.get(Objects.requireNonNull(petId, "petId")));
    }

    /** Returns an immutable point-in-time snapshot of every active trip. */
    public synchronized List<CourierRequest> activeRequests() {
        return List.copyOf(requests.values());
    }

    private CompletableFuture<CourierResult> withRequest(
            UUID requestId,
            PlayerId recipient,
            java.util.function.Function<CourierRequest, CompletableFuture<CourierResult>> action
    ) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(recipient, "recipient");
        CourierRequest request;
        synchronized (this) {
            request = requests.values().stream().filter(r -> r.id().equals(requestId)).findFirst().orElse(null);
        }
        if (request == null) return completed(new CourierResult.UnknownRequest());
        if (!request.recipient().equals(recipient)) return completed(new CourierResult.WrongRecipient());
        return operations.submit(request.owner(), () -> {
            CourierRequest current;
            synchronized (this) { current = requests.get(request.petId()); }
            if (current == null || !current.id().equals(requestId)) {
                return completed(new CourierResult.UnknownRequest());
            }
            return action.apply(current);
        });
    }

    private CompletableFuture<CourierResult> returnLocked(CourierRequest request, CourierResult.Reason reason) {
        CourierRequest current;
        synchronized (this) {
            current = requests.get(request.petId());
            if (current == null || !current.id().equals(request.id())) {
                return completed(new CourierResult.UnknownRequest());
            }
        }
        CourierRequest trip = current;
        return pets.findById(trip.petId()).thenCompose(found -> {
            OwnedPet travelling = found.orElseThrow(() -> new IllegalStateException("Unknown courier pet"));
            OwnedPet returned = travelling.withStatus(PetStatus.ALIVE).withActive(trip.originallyActive());
            return entities.despawn(trip.petId()).thenCompose(ignored -> pets.save(returned))
                    .thenCompose(ignored -> trip.originallyActive()
                            && reason != CourierResult.Reason.OWNER_QUIT
                            ? entities.spawn(trip.petId()).handle((spawned, failure) -> null)
                            : CompletableFuture.completedFuture(null))
                    .thenApply(ignored -> {
                        synchronized (this) { requests.remove(trip.petId(), trip); }
                        return (CourierResult) new CourierResult.Returned(trip, returned, reason);
                    });
        });
    }

    private static Duration requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) throw new IllegalArgumentException(name + " must be positive");
        return duration;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
        return duration;
    }

    private static CompletableFuture<CourierResult> completed(CourierResult result) {
        return CompletableFuture.completedFuture(result);
    }
}
