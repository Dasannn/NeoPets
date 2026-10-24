package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class NeoPetsPlaceholdersTest {
    private final UUID playerId = UUID.randomUUID();

    @Test
    void resolvesEverySupportedIdentifierFromOneSnapshot() {
        var snapshot = new PlaceholderSnapshot(17, 6, 9, 2, 4, Optional.of("Luna"));
        var placeholders = new NeoPetsPlaceholders(
                requested -> requested.equals(playerId) ? Optional.of(snapshot) : Optional.empty(),
                () -> "-");

        assertAll(
                () -> assertEquals("17", placeholders.resolve(playerId, "sp")),
                () -> assertEquals("6", placeholders.resolve(playerId, "sp_max")),
                () -> assertEquals("9", placeholders.resolve(playerId, "pets_owned")),
                () -> assertEquals("2", placeholders.resolve(playerId, "pets_active")),
                () -> assertEquals("4", placeholders.resolve(playerId, "max_active")),
                () -> assertEquals("Luna", placeholders.resolve(playerId, "active_pet_name")));
    }

    @Test
    void usesConfiguredEmptyValueWhenNoPetIsSummoned() {
        var snapshot = new PlaceholderSnapshot(0, 3, 1, 0, 1, Optional.empty());
        var placeholders = new NeoPetsPlaceholders(ignored -> Optional.of(snapshot), () -> "ninguna");

        assertEquals("ninguna", placeholders.resolve(playerId, "active_pet_name"));
    }

    @Test
    void returnsNullForUnknownIdentifierWithoutReadingData() {
        var sourceCalled = new AtomicBoolean();
        var placeholders = new NeoPetsPlaceholders(ignored -> {
            sourceCalled.set(true);
            return Optional.empty();
        }, () -> "-");

        assertNull(placeholders.resolve(playerId, "unknown"));
        assertFalse(sourceCalled.get());
    }

    @Test
    void returnsEmptyStringForMissingPlayerOrUnloadedProfile() {
        var placeholders = new NeoPetsPlaceholders(ignored -> Optional.empty(), () -> "-");

        assertAll(
                () -> assertEquals("", placeholders.resolve(null, "sp")),
                () -> assertEquals("", placeholders.resolve(playerId, "sp")),
                () -> assertEquals("", placeholders.resolve(playerId, "active_pet_name")));
    }
}
