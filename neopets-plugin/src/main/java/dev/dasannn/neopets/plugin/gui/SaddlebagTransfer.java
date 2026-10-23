package dev.dasannn.neopets.plugin.gui;

import org.bukkit.inventory.ItemStack;

/** Plans a one-way stack transfer without mutating either live inventory. */
final class SaddlebagTransfer {
    private SaddlebagTransfer() { }

    static int move(ItemStack[] source, int slot, ItemStack[] destination, boolean half) {
        ItemStack stack = source[slot];
        if (stack == null || stack.isEmpty()) return 0;
        int wanted = half ? (stack.getAmount() + 1) / 2 : stack.getAmount();
        int remaining = wanted;
        // Merge before using empty slots, matching vanilla shift transfer ordering.
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int index = 0; index < destination.length && remaining > 0; index++) {
                ItemStack target = destination[index];
                boolean empty = target == null || target.isEmpty();
                if ((pass == 0 && (empty || !stack.isSimilar(target))) || (pass == 1 && !empty)) continue;
                int count = Math.min(remaining, stack.getMaxStackSize() - (empty ? 0 : target.getAmount()));
                if (count <= 0) continue;
                ItemStack changed = empty ? stack.clone() : target.clone();
                changed.setAmount((empty ? 0 : target.getAmount()) + count);
                destination[index] = changed;
                remaining -= count;
            }
        }
        int moved = wanted - remaining;
        ItemStack leftover = stack.clone();
        leftover.setAmount(stack.getAmount() - moved);
        source[slot] = leftover.getAmount() == 0 ? null : leftover;
        return moved;
    }

    static ItemStack[] copy(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            copy[index] = contents[index] == null ? null : contents[index].clone();
        }
        return copy;
    }
}
