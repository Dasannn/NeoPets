package dev.dasannn.neopets.plugin.runtime;

import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.NodeActivated;
import dev.dasannn.neopets.core.event.TreeReset;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Refreshes effects after skill-tree changes, while avoiding work for dismissed pets. */
final class SkillTreeEffectRefresh implements AutoCloseable {
    private final DomainEventBus.Subscription activated;
    private final DomainEventBus.Subscription reset;

    private SkillTreeEffectRefresh(
            DomainEventBus events,
            LivePetRegistry livePets,
            PetEntityGateway gateway,
            BiConsumer<PetId, Throwable> failureHandler
    ) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(livePets, "livePets");
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(failureHandler, "failureHandler");
        activated = events.subscribe(NodeActivated.class,
                event -> refresh(event.petId(), livePets, gateway, failureHandler));
        reset = events.subscribe(TreeReset.class,
                event -> refresh(event.petId(), livePets, gateway, failureHandler));
    }

    /** Registers refresh listeners and returns their lifecycle handle. */
    static SkillTreeEffectRefresh subscribe(
            DomainEventBus events,
            LivePetRegistry livePets,
            PetEntityGateway gateway,
            BiConsumer<PetId, Throwable> failureHandler
    ) {
        return new SkillTreeEffectRefresh(events, livePets, gateway, failureHandler);
    }

    private static void refresh(
            PetId petId,
            LivePetRegistry livePets,
            PetEntityGateway gateway,
            BiConsumer<PetId, Throwable> failureHandler
    ) {
        if (livePets.entityId(petId).isEmpty()) {
            return;
        }
        gateway.applyEffects(petId).whenComplete((ignored, failure) -> {
            if (failure != null) {
                failureHandler.accept(petId, failure);
            }
        });
    }

    @Override
    public void close() {
        reset.close();
        activated.close();
    }
}
