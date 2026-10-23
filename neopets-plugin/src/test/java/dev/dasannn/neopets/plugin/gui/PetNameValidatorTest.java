package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PetNameValidatorTest {
    @Test
    void acceptsExactlyThirtyTwoVisibleCharactersAfterRemovingMiniMessage() {
        String name = "a".repeat(32);

        assertEquals(name, PetNameValidator.normalize("<red>" + name + "</red>").orElseThrow());
    }

    @Test
    void rejectsMoreThanThirtyTwoVisibleCharacters() {
        assertTrue(PetNameValidator.normalize("<bold>" + "a".repeat(33) + "</bold>").isEmpty());
    }

    @Test
    void countsUnicodeCodePointsAndRejectsLineBreaks() {
        assertEquals("🐕".repeat(32), PetNameValidator.normalize("🐕".repeat(32)).orElseThrow());
        assertTrue(PetNameValidator.normalize("Luna\nSol").isEmpty());
        assertTrue(PetNameValidator.normalize("Luna<newline>Sol").isEmpty());
    }

    @Test
    void rejectsNamesWithNoVisibleCharacters() {
        assertTrue(PetNameValidator.normalize(" <red></red> ").isEmpty());
    }
}
