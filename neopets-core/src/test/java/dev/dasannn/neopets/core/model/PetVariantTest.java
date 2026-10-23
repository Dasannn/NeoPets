package dev.dasannn.neopets.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PetVariantTest {
    private static final VariantId VARIANT_ID = new VariantId("parrot_red");
    private static final PetTypeId TYPE_ID = new PetTypeId("parrot");
    private static final VariantAppearance APPEARANCE = new VariantAppearance(Map.of("color", "red"));

    @Test
    void appearanceDefensivelyCopiesProperties() {
        var source = new HashMap<>(Map.of("color", "red"));
        var appearance = new VariantAppearance(source);

        source.put("color", "blue");

        assertEquals(Map.of("color", "red"), appearance.properties());
        assertThrows(UnsupportedOperationException.class,
                () -> appearance.properties().put("color", "green"));
    }

    @Test
    void appearanceRejectsNulls() {
        assertThrows(NullPointerException.class, () -> new VariantAppearance(null));
        assertThrows(NullPointerException.class,
                () -> new VariantAppearance(java.util.Collections.singletonMap("color", null)));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void variantRequiresPositiveFiniteWeight(double weight) {
        assertThrows(IllegalArgumentException.class,
                () -> new PetVariant(VARIANT_ID, TYPE_ID, APPEARANCE, false, weight, "pet.parrot_red"));
    }

    @Test
    void variantRequiresAllReferenceFields() {
        assertThrows(NullPointerException.class,
                () -> new PetVariant(null, TYPE_ID, APPEARANCE, false, 1.0, "pet.parrot_red"));
        assertThrows(NullPointerException.class,
                () -> new PetVariant(VARIANT_ID, null, APPEARANCE, false, 1.0, "pet.parrot_red"));
        assertThrows(NullPointerException.class,
                () -> new PetVariant(VARIANT_ID, TYPE_ID, null, false, 1.0, "pet.parrot_red"));
        assertThrows(NullPointerException.class,
                () -> new PetVariant(VARIANT_ID, TYPE_ID, APPEARANCE, false, 1.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new PetVariant(VARIANT_ID, TYPE_ID, APPEARANCE, false, 1.0, " "));
    }

    @Test
    void variantRetainsConfiguredValues() {
        var variant = new PetVariant(VARIANT_ID, TYPE_ID, APPEARANCE, true, 2.5, "pet.parrot_red");

        assertEquals(VARIANT_ID, variant.id());
        assertEquals(TYPE_ID, variant.type());
        assertEquals(APPEARANCE, variant.appearance());
        assertEquals(2.5, variant.weight());
        assertEquals("pet.parrot_red", variant.displayNameKey());
    }
}
