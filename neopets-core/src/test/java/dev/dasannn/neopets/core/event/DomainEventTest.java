package dev.dasannn.neopets.core.event;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEventTest {
    private final PetId petId = new PetId(UUID.randomUUID());
    private final PlayerId ownerId = new PlayerId(UUID.randomUUID());

    @Test
    void petEventsRequireTheirIdentifiers() {
        assertThrows(NullPointerException.class, () -> new PetGiven(null, ownerId, new VariantId("cat_black")));
        assertThrows(NullPointerException.class, () -> new PetGiven(petId, null, new VariantId("cat_black")));
        assertThrows(NullPointerException.class, () -> new PetGiven(petId, ownerId, null));
        assertThrows(NullPointerException.class, () -> new PetSummoned(null, ownerId));
        assertThrows(NullPointerException.class, () -> new PetSummoned(petId, null));
        assertThrows(NullPointerException.class, () -> new PetDismissed(null, ownerId));
        assertThrows(NullPointerException.class, () -> new PetDismissed(petId, null));
        assertThrows(NullPointerException.class, () -> new PetDied(null, ownerId));
        assertThrows(NullPointerException.class, () -> new PetDied(petId, null));
        assertThrows(NullPointerException.class, () -> new PetRevived(null, ownerId));
        assertThrows(NullPointerException.class, () -> new PetRevived(petId, null));
    }

    @Test
    void nodeEventsRequirePetAttributeAndPositiveLevel() {
        assertThrows(NullPointerException.class, () -> new NodeUnlocked(null, "health", 1));
        assertThrows(NullPointerException.class, () -> new NodeUnlocked(petId, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new NodeUnlocked(petId, " ", 1));
        assertThrows(IllegalArgumentException.class, () -> new NodeUnlocked(petId, "health", 0));
        assertThrows(NullPointerException.class, () -> new NodeActivated(null, "health", 1));
        assertThrows(NullPointerException.class, () -> new NodeActivated(petId, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new NodeActivated(petId, " ", 1));
        assertThrows(IllegalArgumentException.class, () -> new NodeActivated(petId, "health", -1));
    }

    @Test
    void skillPointEventRequiresPlayerAndNonNegativeBalances() {
        assertThrows(NullPointerException.class, () -> new SkillPointsChanged(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillPointsChanged(ownerId, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillPointsChanged(ownerId, 0, -1));
    }

    @Test
    void everyEventImplementsSealedContract() {
        assertInstanceOf(DomainEvent.class, new PetGiven(petId, ownerId, new VariantId("cat_black")));
        assertInstanceOf(DomainEvent.class, new PetSummoned(petId, ownerId));
        assertInstanceOf(DomainEvent.class, new PetDismissed(petId, ownerId));
        assertInstanceOf(DomainEvent.class, new PetDied(petId, ownerId));
        assertInstanceOf(DomainEvent.class, new PetRevived(petId, ownerId));
        assertInstanceOf(DomainEvent.class, new NodeUnlocked(petId, "health", 1));
        assertInstanceOf(DomainEvent.class, new NodeActivated(petId, "health", 1));
        assertInstanceOf(DomainEvent.class, new SkillPointsChanged(ownerId, 0, 1));
    }
}
