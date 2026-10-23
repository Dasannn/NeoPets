package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownTrackerTest {
    @Test
    void evictionRemovesExpiredEntriesAndPreservesPendingCooldowns() {
        var tracker = new CooldownTracker();
        var expired = new PetId(UUID.randomUUID());
        var pending = new PetId(UUID.randomUUID());
        tracker.claim(expired, "lookout", Duration.ofSeconds(5), Instant.EPOCH);
        tracker.claim(pending, "lookout", Duration.ofSeconds(10), Instant.EPOCH);

        assertEquals(1, tracker.evictExpired(Instant.EPOCH.plusSeconds(5)));
        assertEquals(0, tracker.evictExpired(Instant.EPOCH.plusSeconds(5)));
        assertFalse(tracker.claim(pending, "lookout", Duration.ofSeconds(10), Instant.EPOCH.plusSeconds(5)));
        assertEquals(1, tracker.evictExpired(Instant.EPOCH.plusSeconds(10)));
    }

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
