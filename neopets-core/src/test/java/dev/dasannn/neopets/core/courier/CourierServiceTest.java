package dev.dasannn.neopets.core.courier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CourierServiceTest {
    private static final PlayerId OWNER = player(1);
    private static final PlayerId RECIPIENT = player(2);
    private static final PetId PET = pet(1);
    private final Repository repository = new Repository();
    private final Gateway gateway = new Gateway();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
    private final PlayerOperationQueue operations = new PlayerOperationQueue();
    private CourierService service;

    @BeforeEach
    void setUp() {
        repository.pet = new OwnedPet(PET, OWNER, new VariantId("donkey_brown"), Optional.empty(),
                PetStatus.ALIVE, true, clock.instant());
        service = new CourierService(repository, gateway, operations, clock,
                () -> Duration.ofMinutes(5), () -> Duration.ofMinutes(15));
    }

    @Test
    void requestAcceptAndReturnRestoresOriginalState() {
        var requested = assertInstanceOf(CourierResult.Requested.class,
                service.request(PET, OWNER, RECIPIENT).join());
        assertEquals(PetStatus.IN_TRANSIT, repository.pet.status());
        assertEquals(List.of("despawn"), gateway.actions);

        assertInstanceOf(CourierResult.Accepted.class,
                service.accept(requested.request().id(), RECIPIENT).join());
        assertEquals(List.of("despawn", "courier:" + RECIPIENT), gateway.actions);

        var returned = assertInstanceOf(CourierResult.Returned.class,
                service.returnToOwner(PET, RECIPIENT).join());
        assertEquals(CourierResult.Reason.RECIPIENT_RETURNED, returned.reason());
        assertEquals(PetStatus.ALIVE, repository.pet.status());
        assertTrue(repository.pet.active());
        assertEquals(List.of("despawn", "courier:" + RECIPIENT, "despawn", "spawn"), gateway.actions);
    }

    @Test
    void rejectTimeoutAndRecipientQuitAllReturnThePet() {
        var first = request();
        var rejected = assertInstanceOf(CourierResult.Returned.class,
                service.reject(first.id(), RECIPIENT).join());
        assertEquals(CourierResult.Reason.REJECTED, rejected.reason());

        clock.advance(Duration.ofMinutes(15));
        request();
        clock.advance(Duration.ofMinutes(5));
        assertEquals(CourierResult.Reason.TIMED_OUT, service.expire().join().getFirst().reason());

        clock.advance(Duration.ofMinutes(15));
        CourierRequest third = request();
        service.accept(third.id(), RECIPIENT).join();
        assertEquals(CourierResult.Reason.RECIPIENT_QUIT,
                service.recipientUnavailable(RECIPIENT, CourierResult.Reason.RECIPIENT_QUIT)
                        .join().getFirst().reason());
    }

    @Test
    void successfulRequestClaimsConfiguredCooldown() {
        CourierRequest request = request();
        service.reject(request.id(), RECIPIENT).join();
        var cooling = assertInstanceOf(CourierResult.CoolingDown.class,
                service.request(PET, OWNER, RECIPIENT).join());
        assertEquals(Duration.ofMinutes(15), cooling.remaining());
        clock.advance(Duration.ofMinutes(15));
        assertInstanceOf(CourierResult.Requested.class, service.request(PET, OWNER, RECIPIENT).join());
    }

    @Test
    void queuedAcceptsRecheckTheCurrentRequest() {
        CourierRequest request = request();
        var gate = new CompletableFuture<Void>();
        operations.submit(OWNER, () -> gate);
        var first = service.accept(request.id(), RECIPIENT);
        var second = service.accept(request.id(), RECIPIENT);
        gate.complete(null);
        assertInstanceOf(CourierResult.Accepted.class, first.join());
        assertInstanceOf(CourierResult.AlreadyAccepted.class, second.join());
        assertEquals(1, gateway.actions.stream().filter(action -> action.startsWith("courier:")).count());
    }

    @Test
    void queuedAcceptCannotResurrectARejectedTrip() {
        CourierRequest request = request();
        var gate = new CompletableFuture<Void>();
        operations.submit(OWNER, () -> gate);
        var rejected = service.reject(request.id(), RECIPIENT);
        var accepted = service.accept(request.id(), RECIPIENT);
        gate.complete(null);
        assertInstanceOf(CourierResult.Returned.class, rejected.join());
        assertInstanceOf(CourierResult.UnknownRequest.class, accepted.join());
        assertTrue(service.activeRequests().isEmpty());
        assertEquals(PetStatus.ALIVE, repository.pet.status());
        assertTrue(gateway.actions.stream().noneMatch(action -> action.startsWith("courier:")));
    }

    @Test
    void competingTimeoutAndDisconnectReturnOnlyOnce() {
        request();
        clock.advance(Duration.ofMinutes(5));
        var gate = new CompletableFuture<Void>();
        operations.submit(OWNER, () -> gate);
        var expired = service.expire();
        var quit = service.recipientUnavailable(RECIPIENT, CourierResult.Reason.RECIPIENT_QUIT);
        gate.complete(null);
        assertEquals(1, expired.join().size());
        assertTrue(quit.join().isEmpty());
        assertTrue(service.activeRequests().isEmpty());
    }

    @Test
    void ownerQuitRestoresStatusWithoutSpawningForAnOfflineOwner() {
        CourierRequest request = request();
        service.accept(request.id(), RECIPIENT).join();
        service.ownerUnavailable(OWNER).join();
        assertEquals(PetStatus.ALIVE, repository.pet.status());
        assertTrue(repository.pet.active());
        assertTrue(service.activeRequests().isEmpty());
        assertEquals(List.of("despawn", "courier:" + RECIPIENT, "despawn"), gateway.actions);
    }

    @Test
    void restartRecoveryRestoresOrphanTripsButLeavesLiveRequestsAlone() {
        CourierRequest request = request();
        service.recover(OWNER).join();
        assertEquals(PetStatus.IN_TRANSIT, repository.pet.status());
        assertEquals(request, service.requestFor(PET).orElseThrow());

        var restarted = new CourierService(repository, gateway, new PlayerOperationQueue(), clock,
                () -> Duration.ofMinutes(5), () -> Duration.ofMinutes(15));
        restarted.recover(OWNER).join();
        assertEquals(PetStatus.ALIVE, repository.pet.status());
        assertTrue(repository.pet.active());
    }

    @Test
    void worldChangeReturnsAnInactiveDonkeyWithoutSummoningIt() {
        repository.pet = repository.pet.withActive(false);
        CourierRequest request = request();
        service.accept(request.id(), RECIPIENT).join();
        var returned = service.recipientUnavailable(RECIPIENT, CourierResult.Reason.RECIPIENT_WORLD_CHANGE)
                .join().getFirst();
        assertEquals(CourierResult.Reason.RECIPIENT_WORLD_CHANGE, returned.reason());
        assertTrue(!repository.pet.active());
        assertTrue(!gateway.actions.contains("spawn"));
    }

    @Test
    void staleRejectCannotBypassAcceptedInventorySave() {
        CourierRequest request = request();
        service.accept(request.id(), RECIPIENT).join();
        assertInstanceOf(CourierResult.AlreadyAccepted.class, service.reject(request.id(), RECIPIENT).join());
        assertEquals(PetStatus.IN_TRANSIT, repository.pet.status());
        assertTrue(service.requestFor(PET).orElseThrow().accepted());
    }

    @Test
    void ownerEntityRemainsUntilTransitStateIsDurable() {
        repository.pendingSave = new CompletableFuture<>();
        var requested = service.request(PET, OWNER, RECIPIENT);
        assertTrue(!requested.isDone());
        assertTrue(gateway.actions.isEmpty());
        assertEquals(PetStatus.ALIVE, repository.pet.status());
        repository.pendingSave.complete(null);
        assertInstanceOf(CourierResult.Requested.class, requested.join());
        assertEquals(PetStatus.IN_TRANSIT, repository.pet.status());
        assertEquals(List.of("despawn"), gateway.actions);
    }

    @Test
    void failedTransitSaveLeavesTheOwnerEntityAndCooldownUntouched() {
        var original = repository.pet;
        repository.failSave = true;
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.request(PET, OWNER, RECIPIENT).join());
        assertEquals(original, repository.pet);
        assertTrue(gateway.actions.isEmpty());
        assertTrue(service.activeRequests().isEmpty());
        repository.failSave = false;
        request();
    }

    @Test
    void failedDespawnRestoresTheOwnerStateAndAllowsRetry() {
        var original = repository.pet;
        gateway.failDespawn = true;
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> service.request(PET, OWNER, RECIPIENT).join());
        assertEquals(original, repository.pet);
        assertEquals(List.of("despawn", "spawn"), gateway.actions);
        assertTrue(service.activeRequests().isEmpty());
        gateway.failDespawn = false;
        request();
    }

    private CourierRequest request() {
        return assertInstanceOf(CourierResult.Requested.class,
                service.request(PET, OWNER, RECIPIENT).join()).request();
    }

    private static PlayerId player(int value) {
        return new PlayerId(new UUID(0, value));
    }

    private static PetId pet(int value) {
        return new PetId(new UUID(1, value));
    }

    private static final class Repository implements PetRepository {
        private OwnedPet pet;
        private boolean failSave;
        private CompletableFuture<Void> pendingSave;
        @Override public CompletableFuture<Optional<OwnedPet>> findById(PetId id) {
            return CompletableFuture.completedFuture(pet.id().equals(id) ? Optional.of(pet) : Optional.empty());
        }
        @Override public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId owner) {
            return CompletableFuture.completedFuture(pet.owner().equals(owner) ? List.of(pet) : List.of());
        }
        @Override public CompletableFuture<Void> save(OwnedPet changed) {
            if (failSave) return CompletableFuture.failedFuture(new IllegalStateException("save failed"));
            CompletableFuture<Void> saved = pendingSave == null ? CompletableFuture.completedFuture(null) : pendingSave;
            return saved.thenRun(() -> pet = changed);
        }
        @Override public CompletableFuture<Void> deleteById(PetId id) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class Gateway implements PetEntityGateway {
        private final List<String> actions = new ArrayList<>();
        private boolean failDespawn;
        @Override public CompletableFuture<Void> spawn(PetId petId) {
            actions.add("spawn"); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> spawnForCourier(PetId petId, PlayerId recipient) {
            actions.add("courier:" + recipient); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> despawn(PetId petId) {
            actions.add("despawn");
            return failDespawn ? CompletableFuture.failedFuture(new IllegalStateException("despawn failed"))
                    : CompletableFuture.completedFuture(null);
        }
        @Override public CompletableFuture<Void> applyEffects(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
