package dev.dasannn.neopets.core.skilltree;

/** Business outcome of an administrative skill-point balance mutation. */
public sealed interface SkillPointResult {
    /** The requested mutation was persisted. */
    record Changed(int balance) implements SkillPointResult {
        public Changed {
            if (balance < 0) {
                throw new IllegalArgumentException("balance must not be negative");
            }
        }
    }

    /** A take request exceeded the player's available balance. */
    record InsufficientSkillPoints(int balance, int requested) implements SkillPointResult {
        public InsufficientSkillPoints {
            if (balance < 0 || requested <= balance) {
                throw new IllegalArgumentException("requested must exceed the non-negative balance");
            }
        }
    }

    /** A give request would exceed the supported integer balance range. */
    record Overflow(int balance, int requested) implements SkillPointResult {
        public Overflow {
            if (balance < 0 || requested <= 0) {
                throw new IllegalArgumentException("balance must be non-negative and requested positive");
            }
        }
    }
}
