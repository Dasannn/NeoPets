package dev.dasannn.neopets.api;

import java.util.List;
import java.util.Objects;

/**
 * An immutable, read-only snapshot of a pet's skill tree.
 *
 * @param attributes the progress of each configured attribute
 */
public record SkillTreeView(List<Attribute> attributes) {

    /**
     * Creates a skill-tree snapshot.
     *
     * @param attributes the progress of each configured attribute
     * @throws NullPointerException if {@code attributes}, or one of its elements, is {@code null}
     */
    public SkillTreeView {
        attributes = List.copyOf(attributes);
    }

    /**
     * Returns the progress of each configured attribute.
     *
     * @return an immutable attribute list in definition order
     */
    @Override
    public List<Attribute> attributes() {
        return attributes;
    }

    /**
     * An immutable, read-only snapshot of one skill-tree attribute.
     *
     * @param id the configured attribute identifier
     * @param unlockedLevel the highest level unlocked with money
     * @param activeLevel the highest level activated with skill points
     * @param maxLevel the highest configured level
     */
    public record Attribute(String id, int unlockedLevel, int activeLevel, int maxLevel) {

        /**
         * Creates an attribute snapshot.
         *
         * @param id the configured attribute identifier
         * @param unlockedLevel the highest level unlocked with money
         * @param activeLevel the highest level activated with skill points
         * @param maxLevel the highest configured level
         * @throws NullPointerException if {@code id} is {@code null}
         */
        public Attribute {
            Objects.requireNonNull(id, "id");
        }

        /**
         * Returns the configured attribute identifier.
         *
         * @return the attribute identifier
         */
        @Override
        public String id() {
            return id;
        }

        /**
         * Returns the highest level unlocked with money.
         *
         * @return the unlocked level
         */
        @Override
        public int unlockedLevel() {
            return unlockedLevel;
        }

        /**
         * Returns the highest level activated with skill points.
         *
         * @return the active level
         */
        @Override
        public int activeLevel() {
            return activeLevel;
        }

        /**
         * Returns the highest configured level.
         *
         * @return the maximum level
         */
        @Override
        public int maxLevel() {
            return maxLevel;
        }
    }
}
