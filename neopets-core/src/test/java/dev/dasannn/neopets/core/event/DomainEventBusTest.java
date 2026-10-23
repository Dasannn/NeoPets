package dev.dasannn.neopets.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEventBusTest {
    private final PetId petId = new PetId(UUID.randomUUID());
    private final PlayerId ownerId = new PlayerId(UUID.randomUUID());

    @Test
    void publishesSynchronouslyInSubscriptionOrderForExactType() {
        var bus = new DomainEventBus();
        var calls = new ArrayList<String>();
        bus.subscribe(PetSummoned.class, event -> calls.add("first:" + event.petId()));
        bus.subscribe(PetSummoned.class, event -> calls.add("second:" + event.ownerId()));
        bus.subscribe(PetDied.class, event -> calls.add("wrong type"));

        bus.publish(new PetSummoned(petId, ownerId));

        assertEquals(List.of("first:" + petId, "second:" + ownerId), calls);
    }

    @Test
    void subscriptionCanBeRemovedAndCloseIsIdempotent() {
        var bus = new DomainEventBus();
        var calls = new ArrayList<PetSummoned>();
        var subscription = bus.subscribe(PetSummoned.class, calls::add);
        var event = new PetSummoned(petId, ownerId);

        bus.publish(event);
        subscription.close();
        subscription.unsubscribe();
        bus.publish(event);

        assertEquals(List.of(event), calls);
    }

    @Test
    void rejectsNullArguments() {
        var bus = new DomainEventBus();

        assertThrows(NullPointerException.class, () -> bus.subscribe(null, event -> { }));
        assertThrows(NullPointerException.class, () -> bus.subscribe(PetDied.class, null));
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }

    @Test
    void listenerFailureIsReportedToPublisher() {
        var bus = new DomainEventBus();
        bus.subscribe(PetDied.class, event -> {
            throw new IllegalStateException("listener failed");
        });

        assertThrows(IllegalStateException.class, () -> bus.publish(new PetDied(petId, ownerId)));
    }
}
