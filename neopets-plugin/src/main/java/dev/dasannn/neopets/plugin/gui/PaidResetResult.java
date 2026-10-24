package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.skilltree.ResetResult;
import java.math.BigDecimal;
import java.util.Objects;

/** Outcome of the optional paid player reset flow. */
public sealed interface PaidResetResult {
    record Completed(ResetResult result) implements PaidResetResult {
        public Completed {
            Objects.requireNonNull(result, "result");
        }
    }

    record InsufficientFunds(BigDecimal required) implements PaidResetResult {
        public InsufficientFunds {
            Objects.requireNonNull(required, "required");
            if (required.signum() < 0) {
                throw new IllegalArgumentException("required must not be negative");
            }
        }
    }

    record Failed(Throwable cause) implements PaidResetResult {
        public Failed {
            Objects.requireNonNull(cause, "cause");
        }
    }
}
