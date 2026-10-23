package dev.dasannn.neopets.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierTest {
    @Test
    void acceptsLowercaseKeys() {
        assertEquals("polar_bear", new PetTypeId("polar_bear").value());
        assertEquals("bird01", new VariantId("bird01").value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "Polar_bear", "polar-bear", "_polar", "polar_", "polar__bear"})
    void rejectsInvalidPetTypeKeys(String value) {
        assertThrows(IllegalArgumentException.class, () -> new PetTypeId(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "CAT", "cat-black", "_cat", "cat_", "cat__black"})
    void rejectsInvalidVariantKeys(String value) {
        assertThrows(IllegalArgumentException.class, () -> new VariantId(value));
    }

    @Test
    void rejectsNullIdentifierValues() {
        assertThrows(NullPointerException.class, () -> new PetTypeId(null));
        assertThrows(NullPointerException.class, () -> new VariantId(null));
        assertThrows(NullPointerException.class, () -> new PetId(null));
        assertThrows(NullPointerException.class, () -> new PlayerId(null));
    }

    @Test
    void uuidIdentifiersExposeTheirValues() {
        UUID petUuid = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();

        assertEquals(petUuid, new PetId(petUuid).value());
        assertEquals(playerUuid, new PlayerId(playerUuid).value());
    }
}
