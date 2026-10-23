package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class MenuProtectionTest {
    @Test
    void everyClickTypeIsCancelledInANeoPetsMenu() {
        for (ClickType clickType : ClickType.values()) {
            assertTrue(MenuProtection.cancelsClick(true, clickType), clickType.name());
            assertFalse(MenuProtection.cancelsClick(false, clickType), clickType.name());
        }
    }

    @Test
    void everyDragIsCancelledInANeoPetsMenu() {
        assertTrue(MenuProtection.cancelsDrag(true));
        assertFalse(MenuProtection.cancelsDrag(false));
    }
}
