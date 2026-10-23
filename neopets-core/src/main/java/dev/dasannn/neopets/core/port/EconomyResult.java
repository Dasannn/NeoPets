package dev.dasannn.neopets.core.port;

import java.util.Objects;

/** The result of an economy balance mutation. */
public sealed interface EconomyResult permits EconomyResult.Success, EconomyResult.Failure {
    /** A successful mutation and the resulting account balance. */
    record Success(double balance) implements EconomyResult {
        public Success {
            if (!Double.isFinite(balance)) {
                throw new IllegalArgumentException("balance must be finite");
            }
        }
    }

    /** A rejected mutation with an adapter-provided explanation. */
    record Failure(String reason) implements EconomyResult {
        public Failure {
            Objects.requireNonNull(reason, "reason");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }
}
