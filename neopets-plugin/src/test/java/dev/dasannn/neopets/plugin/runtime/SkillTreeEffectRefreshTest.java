package dev.dasannn.neopets.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.NodeActivated;
import dev.dasannn.neopets.core.event.TreeReset;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SkillTreeEffectRefreshTest {
    @Test
    void activationAndResetRefreshOnlyLivePets() {
        var events = new DomainEventBus();
        var livePets = new LivePetRegistry();
        var gateway = new RecordingGateway();
        PetId live = new PetId(UUID.randomUUID());
        PetId dismissed = new PetId(UUID.randomUUID());
        livePets.register(live, UUID.randomUUID());

        try (var ignored = SkillTreeEffectRefresh.subscribe(
                events, livePets, gateway, (petId, failure) -> {
                    throw new AssertionError(failure);
                })) {
            events.publish(new NodeActivated(live, "health", 1));
            events.publish(new TreeReset(live));
            events.publish(new NodeActivated(dismissed, "health", 1));
            events.publish(new TreeReset(dismissed));
        }

        assertEquals(List.of(live, live), gateway.applied);
    }

    private static final class RecordingGateway implements PetEntityGateway {
        private final List<PetId> applied = new ArrayList<>();

        @Override
        public CompletableFuture<Void> spawn(PetId petId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> despawn(PetId petId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> applyEffects(PetId petId) {
            applied.add(petId);
            return CompletableFuture.completedFuture(null);
        }
    }
}
