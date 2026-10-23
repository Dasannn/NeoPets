package dev.dasannn.neopets.plugin.runtime;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/** Late-bound ports used to preserve composition-root startup order across circular adapters. */
final class DeferredPorts {
    private DeferredPorts() {
    }

    static final class EntityGateway implements PetEntityGateway {
        private final AtomicReference<PetEntityGateway> delegate = new AtomicReference<>();

        void bind(PetEntityGateway value) {
            bindOnce(delegate, value, "pet entity gateway");
        }

        @Override
        public CompletableFuture<Void> spawn(PetId petId) {
            return require(delegate, "pet entity gateway").spawn(petId);
        }

        @Override
        public CompletableFuture<Void> despawn(PetId petId) {
            return require(delegate, "pet entity gateway").despawn(petId);
        }

        @Override
        public CompletableFuture<Void> applyEffects(PetId petId) {
            return require(delegate, "pet entity gateway").applyEffects(petId);
        }
    }

    static final class EconomyPort implements Economy {
        private final AtomicReference<Economy> delegate = new AtomicReference<>();

        void bind(Economy value) {
            bindOnce(delegate, value, "economy");
        }

        @Override
        public CompletableFuture<EconomyResult> has(PlayerId playerId, double amount) {
            return require(delegate, "economy").has(playerId, amount);
        }

        @Override
        public CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount) {
            return require(delegate, "economy").withdraw(playerId, amount);
        }

        @Override
        public CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount) {
            return require(delegate, "economy").deposit(playerId, amount);
        }
    }

    static final class Ranks implements RankLimitsResolver {
        private final AtomicReference<RankLimitsResolver> delegate = new AtomicReference<>();

        void bind(RankLimitsResolver value) {
            bindOnce(delegate, value, "rank limits resolver");
        }

        @Override
        public RankLimits resolve(PlayerId playerId) {
            return require(delegate, "rank limits resolver").resolve(playerId);
        }
    }

    private static <T> void bindOnce(AtomicReference<T> reference, T value, String name) {
        Objects.requireNonNull(value, "value");
        if (!reference.compareAndSet(null, value)) {
            throw new IllegalStateException(name + " is already bound");
        }
    }

    private static <T> T require(AtomicReference<T> reference, String name) {
        T value = reference.get();
        if (value == null) {
            throw new IllegalStateException(name + " is not initialized");
        }
        return value;
    }
}
