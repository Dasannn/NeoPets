package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RecoveryFailureReporter {
    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(1);

    private final ConcurrentHashMap<PetId, Long> lastReports = new ConcurrentHashMap<>();
    private final long intervalNanos;
    private final LongSupplier nanoTime;
    private final BiConsumer<String, Throwable> warningSink;

    RecoveryFailureReporter(Logger logger) {
        this(DEFAULT_INTERVAL, System::nanoTime, loggerSink(logger));
    }

    RecoveryFailureReporter(
            Duration interval,
            LongSupplier nanoTime,
            BiConsumer<String, Throwable> warningSink
    ) {
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("interval must be greater than zero");
        }
        this.intervalNanos = interval.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.warningSink = Objects.requireNonNull(warningSink, "warningSink");
    }

    void report(PetId petId, Throwable failure) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(failure, "failure");
        var now = nanoTime.getAsLong();
        var shouldReport = new AtomicBoolean();
        lastReports.compute(petId, (ignored, previous) -> {
            if (previous == null || now - previous >= intervalNanos || now < previous) {
                shouldReport.set(true);
                return now;
            }
            return previous;
        });
        if (shouldReport.get()) {
            warningSink.accept("Failed to recreate unloaded NeoPets pet " + petId, failure);
        }
    }

    void recovered(PetId petId) {
        lastReports.remove(Objects.requireNonNull(petId, "petId"));
    }

    private static BiConsumer<String, Throwable> loggerSink(Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return (message, failure) -> logger.log(Level.WARNING, message, failure);
    }
}
