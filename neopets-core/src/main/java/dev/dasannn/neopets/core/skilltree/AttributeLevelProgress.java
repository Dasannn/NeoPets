package dev.dasannn.neopets.core.skilltree;

/** The unlocked and active levels of one attribute, bounded by its configured maximum. */
public record AttributeLevelProgress(int unlockedLevel, int activeLevel, int maxLevel) {
    public AttributeLevelProgress {
        if (maxLevel < 1) {
            throw new IllegalArgumentException("maxLevel must be at least one");
        }
        if (unlockedLevel < 0 || unlockedLevel > maxLevel) {
            throw new IllegalArgumentException("unlockedLevel must be between zero and maxLevel");
        }
        if (activeLevel < 0 || activeLevel > unlockedLevel) {
            throw new IllegalArgumentException("activeLevel must be between zero and unlockedLevel");
        }
    }

    /** Returns a copy with a different unlocked level. */
    public AttributeLevelProgress withUnlockedLevel(int newUnlockedLevel) {
        return new AttributeLevelProgress(newUnlockedLevel, activeLevel, maxLevel);
    }

    /** Returns a copy with a different active level. */
    public AttributeLevelProgress withActiveLevel(int newActiveLevel) {
        return new AttributeLevelProgress(unlockedLevel, newActiveLevel, maxLevel);
    }
}
