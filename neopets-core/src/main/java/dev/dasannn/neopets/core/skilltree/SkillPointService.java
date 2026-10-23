package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.SkillPointsChanged;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PlayerRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

/** Manages uncapped player skill-point balances through persistent player profiles. */
public final class SkillPointService {
    private final PlayerRepository playerRepository;
    private final DomainEventBus eventBus;
    private final PlayerOperationQueue operations;

    public SkillPointService(
            PlayerRepository playerRepository, DomainEventBus eventBus, PlayerOperationQueue operations
    ) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    /** Returns a player's balance, treating a profile not yet persisted as zero. */
    public CompletableFuture<Integer> balance(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return balanceUnsequenced(playerId);
    }

    /** Adds a positive number of points and returns the resulting balance. */
    public CompletableFuture<Integer> give(PlayerId playerId, int amount) {
        requirePositive(amount, "amount");
        Objects.requireNonNull(playerId, "playerId");
        return operations.submit(playerId, () -> applyDelta(playerId, amount));
    }

    /** Removes a positive number of points and returns the resulting balance. */
    public CompletableFuture<Integer> take(PlayerId playerId, int amount) {
        requirePositive(amount, "amount");
        Objects.requireNonNull(playerId, "playerId");
        return operations.submit(playerId, () -> applyDelta(playerId, -amount));
    }

    /** Replaces a player's balance with a non-negative value and returns it. */
    public CompletableFuture<Integer> set(PlayerId playerId, int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        Objects.requireNonNull(playerId, "playerId");
        return operations.submit(playerId, () -> updateUnsequenced(playerId, ignored -> balance));
    }

    CompletableFuture<Integer> balanceUnsequenced(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return balanceProfile(playerId)
                .thenApply(profile -> profile.map(PlayerProfile::skillPointBalance).orElse(0));
    }

    CompletableFuture<Integer> applyDelta(PlayerId playerId, int delta) {
        Objects.requireNonNull(playerId, "playerId");
        return updateUnsequenced(playerId, balance -> {
            final int changed;
            try {
                changed = Math.addExact(balance, delta);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "skill-point balance exceeds the supported integer range", exception);
            }
            if (changed < 0) {
                throw new IllegalArgumentException("cannot take more skill points than the player has");
            }
            return changed;
        });
    }

    boolean uses(PlayerOperationQueue operationQueue) {
        return operations == operationQueue;
    }

    private CompletableFuture<Integer> updateUnsequenced(PlayerId playerId, IntUnaryOperator mutation) {
        return balanceProfile(playerId).thenCompose(foundProfile -> {
            var profile = foundProfile.orElseGet(() -> new PlayerProfile(playerId, 0, List.of()));
            int previousBalance = profile.skillPointBalance();
            int newBalance = mutation.applyAsInt(previousBalance);
            if (newBalance == previousBalance) {
                return CompletableFuture.completedFuture(newBalance);
            }

            return playerRepository.save(profile.withSkillPointBalance(newBalance)).thenApply(ignored -> {
                eventBus.publish(new SkillPointsChanged(playerId, previousBalance, newBalance));
                return newBalance;
            });
        });
    }

    private CompletableFuture<Optional<PlayerProfile>> balanceProfile(PlayerId playerId) {
        try {
            return Objects.requireNonNull(playerRepository.findById(playerId), "profile lookup future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }
}
