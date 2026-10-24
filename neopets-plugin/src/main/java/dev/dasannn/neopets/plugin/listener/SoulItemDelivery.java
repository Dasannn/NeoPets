package dev.dasannn.neopets.plugin.listener;

import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Gives an item to an inventory and drops only inventory leftovers at the recipient's feet. */
public final class SoulItemDelivery<T> {
    /** Delivery destination independent of Bukkit inventory capacity calculations. */
    public interface Recipient<T> {
        /** Offers the item and returns slot-indexed leftovers that did not fit. */
        Map<Integer, T> add(T item);

        /** Drops one leftover stack at the recipient's current feet. */
        void dropAtFeet(T item);
    }

    /** Result used to select the appropriate owner notification. */
    public enum Result {
        INVENTORY,
        DROPPED
    }

    /** Delivers one item and reports whether any part had to be dropped. */
    public Result deliver(T item, Recipient<T> recipient) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(recipient, "recipient");
        Map<Integer, T> leftovers = Map.copyOf(
                Objects.requireNonNull(recipient.add(item), "inventory leftovers"));
        leftovers.values().forEach(recipient::dropAtFeet);
        return leftovers.isEmpty() ? Result.INVENTORY : Result.DROPPED;
    }

    /** Adapts an online Bukkit player to a delivery recipient. */
    public static Recipient<ItemStack> player(Player player) {
        Objects.requireNonNull(player, "player");
        return new Recipient<>() {
            @Override
            public Map<Integer, ItemStack> add(ItemStack item) {
                return player.getInventory().addItem(item);
            }

            @Override
            public void dropAtFeet(ItemStack item) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        };
    }
}
