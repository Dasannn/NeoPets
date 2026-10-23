package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillTreeRegistryTest {
    @Test
    void findsDefinitionsByPetType() {
        var cat = new SkillTreeDefinition(new PetTypeId("cat"), List.of());
        SkillTreeRegistry registry = new InMemorySkillTreeRegistry(List.of(cat));

        assertEquals(cat, registry.findByPetType(new PetTypeId("cat")).orElseThrow());
        assertTrue(registry.findByPetType(new PetTypeId("dog")).isEmpty());
    }

    @Test
    void validatesRegistryInputAndLookup() {
        var cat = new SkillTreeDefinition(new PetTypeId("cat"), List.of());

        assertThrows(NullPointerException.class, () -> new InMemorySkillTreeRegistry(null));
        assertThrows(NullPointerException.class,
                () -> new InMemorySkillTreeRegistry(List.of((SkillTreeDefinition) null)));
        assertThrows(IllegalArgumentException.class,
                () -> new InMemorySkillTreeRegistry(List.of(cat, cat)));

        var registry = new InMemorySkillTreeRegistry(List.of());
        assertThrows(NullPointerException.class, () -> registry.findByPetType(null));
    }
}
