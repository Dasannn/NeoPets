package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GiveArgumentTest {
    @Test
    void parsesOneSpecificVariant() {
        var parsed = assertInstanceOf(GiveArgument.Specific.class, GiveArgument.parse("dog_pale"));
        assertEquals(new VariantId("dog_pale"), parsed.variantId());
    }

    @Test
    void parsesAndDeduplicatesCandidateListInInputOrder() {
        var parsed = assertInstanceOf(GiveArgument.Candidates.class,
                GiveArgument.parse("dog_pale,cat_black,dog_pale"));
        assertEquals(List.of(new VariantId("dog_pale"), new VariantId("cat_black")), parsed.variantIds());
    }

    @Test
    void parsesRandomWithAndWithoutType() {
        assertEquals(Optional.empty(),
                assertInstanceOf(GiveArgument.Random.class, GiveArgument.parse("random")).typeId());
        assertEquals(Optional.of(new PetTypeId("polar_bear")),
                assertInstanceOf(GiveArgument.Random.class, GiveArgument.parse("random", "polar_bear")).typeId());
    }

    @Test
    void rejectsMalformedSelections() {
        assertThrows(IllegalArgumentException.class, GiveArgument::parse);
        assertThrows(IllegalArgumentException.class, () -> GiveArgument.parse("dog_pale", "cat_black"));
        assertThrows(IllegalArgumentException.class, () -> GiveArgument.parse("dog_pale,"));
        assertThrows(IllegalArgumentException.class, () -> GiveArgument.parse("random", "dog", "extra"));
    }
}
