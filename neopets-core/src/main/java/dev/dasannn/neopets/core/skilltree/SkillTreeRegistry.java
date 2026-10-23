package dev.dasannn.neopets.core.skilltree;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.util.Optional;

/** Read-only lookup of skill-tree definitions by pet type. */
@FunctionalInterface
public interface SkillTreeRegistry {
    Optional<SkillTreeDefinition> findByPetType(PetTypeId petTypeId);
}
