package dev.dasannn.neopets.plugin.gui;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable chest-menu data that can be rendered in game or by the preview tool. */
public record MenuLayout(String title, int rows, Map<Integer, MenuItem> items) {
    public MenuLayout {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(items, "items");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6");
        }
        int size = rows * 9;
        var ordered = new TreeMap<Integer, MenuItem>();
        items.forEach((slot, item) -> {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(item, "item");
            if (slot < 0 || slot >= size) {
                throw new IllegalArgumentException("slot " + slot + " is outside a " + rows + " row menu");
            }
            ordered.put(slot, item);
        });
        items = Collections.unmodifiableMap(ordered);
    }

    /** Returns the total inventory size represented by this layout. */
    public int size() {
        return rows * 9;
    }
}
