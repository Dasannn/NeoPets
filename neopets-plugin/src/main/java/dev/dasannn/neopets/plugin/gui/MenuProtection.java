package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import org.bukkit.event.inventory.ClickType;

final class MenuProtection {
    private MenuProtection() {
    }

    static boolean cancelsClick(boolean neoPetsMenu, ClickType clickType) {
        Objects.requireNonNull(clickType, "clickType");
        return neoPetsMenu;
    }

    static boolean cancelsDrag(boolean neoPetsMenu) {
        return neoPetsMenu;
    }
}
