package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RecoveryFailureReporterTest {
    @Test
    void rateLimitsPersistentFailuresAndReportsAgainAfterRecovery() {
        var now = new AtomicLong(100L);
        var warnings = new ArrayList<String>();
        var reporter = new RecoveryFailureReporter(
                Duration.ofNanos(10L), now::get, (message, failure) -> warnings.add(message));
        var petId = new PetId(UUID.randomUUID());

        reporter.report(petId, new IllegalStateException("first"));
        now.set(109L);
        reporter.report(petId, new IllegalStateException("suppressed"));
        now.set(110L);
        reporter.report(petId, new IllegalStateException("interval elapsed"));
        reporter.recovered(petId);
        reporter.report(petId, new IllegalStateException("new failure sequence"));

        assertEquals(3, warnings.size());
    }
}
