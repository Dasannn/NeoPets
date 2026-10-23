package dev.dasannn.neopets.plugin.gui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, server-independent description of one menu item. */
public record MenuItem(
        String material,
        String name,
        List<String> lore,
        boolean glint,
        int amount,
        Optional<String> actionId
) {
    public MenuItem {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(lore, "lore");
        Objects.requireNonNull(actionId, "actionId");
        if (material.isBlank()) {
            throw new IllegalArgumentException("material must not be blank");
        }
        if (amount < 1 || amount > 99) {
            throw new IllegalArgumentException("amount must be between 1 and 99");
        }
        lore = List.copyOf(lore);
        actionId.ifPresent(action -> {
            if (action.isBlank()) {
                throw new IllegalArgumentException("actionId must not be blank when present");
            }
        });
    }

    /** Creates a decorative item without lore, glint, or an action. */
    public static MenuItem decoration(String material, String name) {
        return new MenuItem(material, name, List.of(), false, 1, Optional.empty());
    }

    /** Creates an actionable item with the default amount of one. */
    public static MenuItem action(
            String material, String name, List<String> lore, boolean glint, String actionId
    ) {
        return new MenuItem(material, name, lore, glint, 1, Optional.of(actionId));
    }
}
