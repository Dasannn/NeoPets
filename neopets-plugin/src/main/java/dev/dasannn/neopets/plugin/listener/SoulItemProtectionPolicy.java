package dev.dasannn.neopets.plugin.listener;

import java.util.Set;

/** Pure constant-time decisions for moving a tagged soul item through inventory views. */
public final class SoulItemProtectionPolicy {
    /** Inventory station category relevant to soul-item protection. */
    public enum Station {
        NORMAL,
        CRAFTING_TABLE,
        PLAYER_CRAFTING,
        CRAFTER,
        ANVIL,
        SMITHING,
        GRINDSTONE,
        ENCHANTING,
        STONECUTTER,
        LOOM,
        CARTOGRAPHY,
        FURNACE,
        SMOKER,
        BLAST_FURNACE,
        BREWING,
        DECORATED_POT,
        CHISELED_BOOKSHELF,
        COMPOSTER
    }

    /** Inventory action category relevant to inserting an item. */
    public enum Action {
        PLACE,
        HOTBAR_SWAP,
        SHIFT_MOVE,
        PICKUP,
        OTHER
    }

    /** Location of the soul item involved in one inventory action. */
    public enum SoulLocation {
        CURSOR,
        TOP,
        BOTTOM,
        HOTBAR
    }

    private SoulItemProtectionPolicy() {
    }

    /** Returns whether a soul-item action is safe for the selected station and slot. */
    public static boolean allows(
            Station station,
            int rawSlot,
            int topSize,
            Action action,
            SoulLocation soulLocation
    ) {
        if (station == Station.NORMAL) {
            return true;
        }
        boolean protectedTarget = rawSlot >= 0 && rawSlot < topSize && isProtectedSlot(station, rawSlot);
        return switch (soulLocation) {
            case CURSOR -> !protectedTarget || action != Action.PLACE;
            case HOTBAR -> !protectedTarget || action != Action.HOTBAR_SWAP;
            case BOTTOM -> action != Action.SHIFT_MOVE;
            case TOP -> true; // Taking a soul back out is deliberately allowed.
        };
    }

    /** Returns whether automation may insert an item into this station. */
    public static boolean allowsAutomatedInsertion(Station station) {
        return station == Station.NORMAL;
    }

    /** Returns whether dragging a soul while this station is open is safe. */
    public static boolean allowsDrag(Station station, int topSize, Set<Integer> rawSlots) {
        // Only stations' fixed input slots matter, regardless of the number of dragged slots.
        for (int slot = 0; slot < Math.min(topSize, 10); slot++) {
            if (isProtectedSlot(station, slot) && rawSlots.contains(slot)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isProtectedSlot(Station station, int slot) {
        return switch (station) {
            case CRAFTING_TABLE -> slot >= 1 && slot <= 9;
            case PLAYER_CRAFTING -> slot >= 1 && slot <= 4;
            case CRAFTER -> slot >= 0 && slot <= 8;
            case ANVIL, GRINDSTONE, CARTOGRAPHY -> slot == 0 || slot == 1;
            case SMITHING -> slot >= 0 && slot <= 2;
            case ENCHANTING -> slot == 0 || slot == 1;
            case STONECUTTER -> slot == 0;
            case LOOM -> slot >= 0 && slot <= 2;
            case FURNACE, SMOKER, BLAST_FURNACE -> slot == 0 || slot == 1;
            case BREWING -> slot >= 0 && slot <= 4;
            case DECORATED_POT, COMPOSTER -> slot == 0;
            case CHISELED_BOOKSHELF -> slot >= 0 && slot <= 5;
            case NORMAL -> false;
        };
    }
}
