package dev.dasannn.neopets.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        assertThrows(NullPointerException.class, () -> new DomainEventBus(null));
        assertThrows(NullPointerException.class, () -> bus.subscribe(null, event -> { }));
        assertThrows(NullPointerException.class, () -> bus.subscribe(PetDied.class, null));
        assertThrows(NullPointerException.class, () -> bus.publish(null));
    }

    @Test
    void listenerFailureIsReportedAndDoesNotInterruptDelivery() {
        var failures = new ArrayList<Throwable>();
        var failedEvents = new ArrayList<DomainEvent>();
        var bus = new DomainEventBus((event, exception) -> {
            failedEvents.add(event);
            failures.add(exception);
        });
        var delivered = new ArrayList<PetSummoned>();
        var event = new PetSummoned(petId, ownerId);
        var failure = new IllegalStateException("listener failed");
        bus.subscribe(PetSummoned.class, ignored -> {
            throw failure;
        });
        bus.subscribe(PetSummoned.class, delivered::add);

        bus.publish(event);

        assertEquals(List.of(event), failedEvents);
        assertEquals(List.of(event), delivered);
        assertSame(failure, failures.getFirst());
    }

    @Test
    void errorHandlerFailureDoesNotInterruptDelivery() {
        var bus = new DomainEventBus((event, exception) -> {
            throw new IllegalArgumentException("handler failed");
        });
        var delivered = new ArrayList<PetDied>();
        bus.subscribe(PetDied.class, event -> {
            throw new IllegalStateException("listener failed");
        });
        bus.subscribe(PetDied.class, delivered::add);
        var event = new PetDied(petId, ownerId);

        bus.publish(event);

        assertEquals(List.of(event), delivered);
    }
}
