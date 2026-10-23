package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable skill-tree progress for one owned pet. */
public record TreeProgress(PetId petId, Map<String, AttributeLevelProgress> attributes) {
    public TreeProgress {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(attributes, "attributes");

        var immutableAttributes = new LinkedHashMap<String, AttributeLevelProgress>();
        attributes.forEach((attributeId, progress) -> immutableAttributes.put(
                SkillTreeValidation.requireLowercaseKey(attributeId, "attributeId"),
                Objects.requireNonNull(progress, "attribute progress")));
        attributes = Collections.unmodifiableMap(immutableAttributes);
    }

    /** Creates zeroed progress containing every attribute in definition order. */
    public static TreeProgress empty(PetId petId, SkillTreeDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        var attributes = new LinkedHashMap<String, AttributeLevelProgress>();
        for (AttributeDefinition attribute : definition.attributes()) {
            attributes.put(attribute.id(), new AttributeLevelProgress(0, 0, attribute.maxLevel()));
        }
        return new TreeProgress(petId, attributes);
    }

    /** Returns the number of purchased nodes. */
    public int unlockedNodes() {
        return attributes.values().stream().mapToInt(AttributeLevelProgress::unlockedLevel).sum();
    }

    /** Returns the number of active nodes, equal to skill points assigned to this pet. */
    public int activeNodes() {
        return attributes.values().stream().mapToInt(AttributeLevelProgress::activeLevel).sum();
    }

    /** Returns a copy with the unlocked level changed for one existing attribute. */
    public TreeProgress withUnlockedLevel(String attributeId, int unlockedLevel) {
        var current = requireAttribute(attributeId);
        return withAttribute(attributeId, current.withUnlockedLevel(unlockedLevel));
    }

    /** Returns a copy with the active level changed for one existing attribute. */
    public TreeProgress withActiveLevel(String attributeId, int activeLevel) {
        var current = requireAttribute(attributeId);
        return withAttribute(attributeId, current.withActiveLevel(activeLevel));
    }

    /** Returns a copy with both levels changed for one existing attribute. */
    public TreeProgress withLevels(String attributeId, int unlockedLevel, int activeLevel) {
        var current = requireAttribute(attributeId);
        return withAttribute(attributeId,
                new AttributeLevelProgress(unlockedLevel, activeLevel, current.maxLevel()));
    }

    private AttributeLevelProgress requireAttribute(String attributeId) {
        String validatedId = SkillTreeValidation.requireLowercaseKey(attributeId, "attributeId");
        var progress = attributes.get(validatedId);
        if (progress == null) {
            throw new IllegalArgumentException("unknown attribute: " + validatedId);
        }
        return progress;
    }

    private TreeProgress withAttribute(String attributeId, AttributeLevelProgress progress) {
        var changedAttributes = new LinkedHashMap<>(attributes);
        changedAttributes.put(attributeId, progress);
        return new TreeProgress(petId, changedAttributes);
    }
}
