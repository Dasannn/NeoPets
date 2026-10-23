package dev.dasannn.neopets.core.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantAppearance;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVariantCatalogTest {
    private static final PetTypeId DOG = new PetTypeId("dog");
    private static final PetTypeId CAT = new PetTypeId("cat");

    @Test
    void supportsLookupAndOrderedListings() {
        var first = variant("first", DOG);
        var second = variant("second", CAT);
        var third = variant("third", DOG);
        var catalog = new InMemoryVariantCatalog(List.of(first, second, third));

        assertEquals(first, catalog.findById(first.id()).orElseThrow());
        assertEquals(List.of(first, second, third), catalog.findAll());
        assertEquals(List.of(first, third), catalog.findByType(DOG));
        assertEquals(List.of(), catalog.findByType(new PetTypeId("parrot")));
    }

    @Test
    void rejectsDuplicateVariantIdentifiers() {
        var first = variant("same", DOG);
        var duplicate = variant("same", CAT);

        assertThrows(IllegalArgumentException.class,
                () -> new InMemoryVariantCatalog(List.of(first, duplicate)));
    }

    private static PetVariant variant(String id, PetTypeId type) {
        return new PetVariant(new VariantId(id), type, VariantAppearance.empty(), false, 1.0, "pet." + id);
    }
}
