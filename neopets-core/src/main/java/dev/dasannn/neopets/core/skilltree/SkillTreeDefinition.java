package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** The ordered attribute definitions configured for one pet type. */
public record SkillTreeDefinition(PetTypeId petTypeId, List<AttributeDefinition> attributes) {
    public SkillTreeDefinition {
        Objects.requireNonNull(petTypeId, "petTypeId");
        Objects.requireNonNull(attributes, "attributes");
        attributes = List.copyOf(attributes);

        Set<String> attributeIds = new HashSet<>();
        for (AttributeDefinition attribute : attributes) {
            if (!attributeIds.add(attribute.id())) {
                throw new IllegalArgumentException("attribute ids must be unique within a skill tree");
            }
        }
    }

    /** Returns the total number of nodes across all attributes. */
    public int totalNodes() {
        return attributes.stream().mapToInt(AttributeDefinition::maxLevel).sum();
    }
}
