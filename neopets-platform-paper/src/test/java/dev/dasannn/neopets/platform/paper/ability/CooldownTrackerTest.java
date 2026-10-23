package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {
    @Test
    void cooldownsAreIndependentPerPetAndAbilityAndReadyAtTheBoundary() {
        var tracker = new CooldownTracker();
        var firstPet = new PetId(UUID.randomUUID());
        var secondPet = new PetId(UUID.randomUUID());
        var start = Instant.parse("2026-10-23T12:00:00Z");

        assertTrue(tracker.claim(firstPet, "claw_swipe", Duration.ofSeconds(10), start));
        assertFalse(tracker.claim(firstPet, "claw_swipe", Duration.ofSeconds(10), start.plusSeconds(9)));
        assertTrue(tracker.claim(firstPet, "lookout", Duration.ofSeconds(10), start.plusSeconds(1)));
        assertTrue(tracker.claim(secondPet, "claw_swipe", Duration.ofSeconds(10), start.plusSeconds(1)));
        assertTrue(tracker.claim(firstPet, "claw_swipe", Duration.ofSeconds(10), start.plusSeconds(10)));
    }

    @Test
    void clearingOnePetMakesItsCooldownImmediatelyReady() {
        var tracker = new CooldownTracker();
        var petId = new PetId(UUID.randomUUID());
        var start = Instant.parse("2026-10-23T12:00:00Z");
        tracker.claim(petId, "lookout", Duration.ofMinutes(1), start);

        tracker.clear(petId);

        assertTrue(tracker.claim(petId, "lookout", Duration.ofMinutes(1), start));
    }
}
