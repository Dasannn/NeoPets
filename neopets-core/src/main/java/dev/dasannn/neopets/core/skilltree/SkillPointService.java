package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.SkillPointsChanged;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PlayerRepository;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;

/** Manages uncapped player skill-point balances through persistent player profiles. */
public final class SkillPointService {
    private final PlayerRepository playerRepository;
    private final DomainEventBus eventBus;

    public SkillPointService(PlayerRepository playerRepository, DomainEventBus eventBus) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    /** Returns a player's balance, treating a profile not yet persisted as zero. */
    public CompletableFuture<Integer> balance(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return playerRepository.findById(playerId)
                .thenApply(profile -> profile.map(PlayerProfile::skillPointBalance).orElse(0));
    }

    /** Adds a positive number of points and returns the resulting balance. */
    public CompletableFuture<Integer> give(PlayerId playerId, int amount) {
        requirePositive(amount, "amount");
        return update(playerId, balance -> {
            try {
                return Math.addExact(balance, amount);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("skill-point balance exceeds the supported integer range", exception);
            }
        });
    }

    /** Removes a positive number of points and returns the resulting balance. */
    public CompletableFuture<Integer> take(PlayerId playerId, int amount) {
        requirePositive(amount, "amount");
        return update(playerId, balance -> {
            if (amount > balance) {
                throw new IllegalArgumentException("cannot take more skill points than the player has");
            }
            return balance - amount;
        });
    }

    /** Replaces a player's balance with a non-negative value and returns it. */
    public CompletableFuture<Integer> set(PlayerId playerId, int balance) {
        if (balance < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        return update(playerId, ignored -> balance);
    }

    private CompletableFuture<Integer> update(PlayerId playerId, IntUnaryOperator mutation) {
        Objects.requireNonNull(playerId, "playerId");
        return playerRepository.findById(playerId).thenCompose(foundProfile -> {
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

    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than zero");
        }
    }
}
