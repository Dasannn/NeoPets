package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.SkillPointsChanged;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PlayerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class SkillPointServiceTest {
    private final PlayerId playerId = new PlayerId(UUID.randomUUID());

    @Test
    void missingProfileHasZeroBalanceAndIsCreatedOnGive() {
        var repository = new InMemoryPlayerRepository();
        var events = new ArrayList<SkillPointsChanged>();
        var eventBus = new DomainEventBus();
        eventBus.subscribe(SkillPointsChanged.class, events::add);
        var service = new SkillPointService(repository, eventBus);

        assertEquals(0, service.balance(playerId).join());
        assertEquals(5, service.give(playerId, 5).join());

        assertEquals(5, repository.profile.orElseThrow().skillPointBalance());
        assertEquals(List.of(new SkillPointsChanged(playerId, 0, 5)), events);
    }

    @Test
    void giveTakeAndSetPersistAndPublishEveryChangedBalance() {
        var repository = new InMemoryPlayerRepository(new PlayerProfile(playerId, 4, List.of()));
        var events = new ArrayList<SkillPointsChanged>();
        var eventBus = new DomainEventBus();
        eventBus.subscribe(SkillPointsChanged.class, events::add);
        var service = new SkillPointService(repository, eventBus);

        assertEquals(7, service.give(playerId, 3).join());
        assertEquals(5, service.take(playerId, 2).join());
        assertEquals(20, service.set(playerId, 20).join());
        assertEquals(20, service.set(playerId, 20).join());

        assertEquals(20, service.balance(playerId).join());
        assertEquals(3, repository.saveCount);
        assertEquals(List.of(
                new SkillPointsChanged(playerId, 4, 7),
                new SkillPointsChanged(playerId, 7, 5),
                new SkillPointsChanged(playerId, 5, 20)
        ), events);
    }

    @Test
    void balanceHasNoConfiguredCapButRejectsIntegerOverflow() {
        var repository = new InMemoryPlayerRepository(
                new PlayerProfile(playerId, Integer.MAX_VALUE - 1, List.of()));
        var service = new SkillPointService(repository, new DomainEventBus());

        assertEquals(Integer.MAX_VALUE, service.give(playerId, 1).join());
        assertThrows(CompletionException.class, () -> service.give(playerId, 1).join());
    }

    @Test
    void validatesAmountsAndAvailableBalance() {
        var service = new SkillPointService(
                new InMemoryPlayerRepository(new PlayerProfile(playerId, 2, List.of())),
                new DomainEventBus());

        assertThrows(IllegalArgumentException.class, () -> service.give(playerId, 0));
        assertThrows(IllegalArgumentException.class, () -> service.give(playerId, -1));
        assertThrows(IllegalArgumentException.class, () -> service.take(playerId, 0));
        assertThrows(IllegalArgumentException.class, () -> service.take(playerId, -1));
        assertThrows(IllegalArgumentException.class, () -> service.set(playerId, -1));
        assertThrows(CompletionException.class, () -> service.take(playerId, 3).join());
        assertEquals(2, service.balance(playerId).join());
    }

    @Test
    void requiresDependenciesAndPlayerIds() {
        var repository = new InMemoryPlayerRepository();
        var eventBus = new DomainEventBus();
        assertThrows(NullPointerException.class, () -> new SkillPointService(null, eventBus));
        assertThrows(NullPointerException.class, () -> new SkillPointService(repository, null));

        var service = new SkillPointService(repository, eventBus);
        assertThrows(NullPointerException.class, () -> service.balance(null));
        assertThrows(NullPointerException.class, () -> service.give(null, 1));
        assertThrows(NullPointerException.class, () -> service.take(null, 1));
        assertThrows(NullPointerException.class, () -> service.set(null, 1));
    }

    @Test
    void publishesOnlyAfterSuccessfulPersistence() {
        var repository = new InMemoryPlayerRepository(new PlayerProfile(playerId, 1, List.of()));
        repository.saveFailure = new IllegalStateException("database unavailable");
        var events = new ArrayList<SkillPointsChanged>();
        var eventBus = new DomainEventBus();
        eventBus.subscribe(SkillPointsChanged.class, events::add);
        var service = new SkillPointService(repository, eventBus);

        assertThrows(CompletionException.class, () -> service.give(playerId, 1).join());
        assertEquals(List.of(), events);
    }

    private static final class InMemoryPlayerRepository implements PlayerRepository {
        private Optional<PlayerProfile> profile;
        private RuntimeException saveFailure;
        private int saveCount;

        private InMemoryPlayerRepository() {
            this.profile = Optional.empty();
        }

        private InMemoryPlayerRepository(PlayerProfile profile) {
            this.profile = Optional.of(profile);
        }

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
            return CompletableFuture.completedFuture(profile.filter(value -> value.playerId().equals(playerId)));
        }

        @Override
        public CompletableFuture<Void> save(PlayerProfile profile) {
            if (saveFailure != null) {
                return CompletableFuture.failedFuture(saveFailure);
            }
            this.profile = Optional.of(profile);
            saveCount++;
            return CompletableFuture.completedFuture(null);
        }
    }
}
