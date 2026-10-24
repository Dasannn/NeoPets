package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PlaceholderApiRegistrationTest {
    @Test
    void absentPlaceholderApiDoesNotTouchItsRegistrar() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion"));
        assertDoesNotThrow(() -> Class.forName("dev.dasannn.neopets.plugin.runtime.NeoPetsRuntime"));
        var registration = assertDoesNotThrow(() -> PlaceholderApiRegistration.registerIfPresent(false, () -> {
            throw new NoClassDefFoundError("me/clip/placeholderapi/expansion/PlaceholderExpansion");
        }));

        assertTrue(registration.isEmpty());
    }

    @Test
    void presentPlaceholderApiRegistersAndReturnsShutdownAction() throws Exception {
        var closed = new AtomicBoolean();
        var registration = PlaceholderApiRegistration.registerIfPresent(true, () -> () -> closed.set(true));

        assertTrue(registration.isPresent());
        assertFalse(closed.get());
        registration.orElseThrow().close();
        assertTrue(closed.get());
    }
}
