package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An immutable in-memory skill-tree registry, suitable for configured definitions and tests. */
public final class InMemorySkillTreeRegistry implements SkillTreeRegistry {
    private final Map<PetTypeId, SkillTreeDefinition> definitions;

    public InMemorySkillTreeRegistry(Collection<SkillTreeDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        var indexedDefinitions = new LinkedHashMap<PetTypeId, SkillTreeDefinition>();
        for (SkillTreeDefinition definition : definitions) {
            Objects.requireNonNull(definition, "definition");
            if (indexedDefinitions.putIfAbsent(definition.petTypeId(), definition) != null) {
                throw new IllegalArgumentException("pet types must be unique within a skill-tree registry");
            }
        }
        this.definitions = Map.copyOf(indexedDefinitions);
    }

    @Override
    public Optional<SkillTreeDefinition> findByPetType(PetTypeId petTypeId) {
        Objects.requireNonNull(petTypeId, "petTypeId");
        return Optional.ofNullable(definitions.get(petTypeId));
    }
}
