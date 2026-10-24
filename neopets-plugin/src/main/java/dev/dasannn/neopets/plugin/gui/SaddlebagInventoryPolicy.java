package dev.dasannn.neopets.plugin.gui;

import org.bukkit.event.inventory.ClickType;

/** Only explicit one-way gestures are translated into durable inventory transfers. */
final class SaddlebagInventoryPolicy {
    private SaddlebagInventoryPolicy() { }

    static boolean transfers(ClickType click) {
        return click == ClickType.LEFT || click == ClickType.RIGHT
                || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
    }
}
