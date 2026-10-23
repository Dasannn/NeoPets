package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PetFilterTest {
    @Test
    void cyclesInTheApprovedSpanishDisplayOrder() {
        var visited = new java.util.ArrayList<PetFilter>();
        PetFilter filter = PetFilter.ALL;
        for (int index = 0; index < PetFilter.values().length; index++) {
            visited.add(filter);
            filter = filter.next();
        }

        assertEquals(List.of(PetFilter.ALL, PetFilter.DOG, PetFilter.CAT, PetFilter.PARROT,
                PetFilter.POLAR_BEAR, PetFilter.DONKEY), visited);
        assertEquals(PetFilter.ALL, filter);
    }

    @Test
    void allIncludesEveryTypeAndSpecificFiltersOnlyTheirType() {
        assertTrue(PetFilter.ALL.includes("parrot"));
        assertTrue(PetFilter.PARROT.includes("parrot"));
        assertFalse(PetFilter.PARROT.includes("cat"));
    }
}
