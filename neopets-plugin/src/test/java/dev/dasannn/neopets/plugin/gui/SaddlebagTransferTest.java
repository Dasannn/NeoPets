package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class SaddlebagTransferTest {
    @Test
    void mergesThenFillsEmptySlotsWithoutLosingRemainder() {
        ItemStack[] source = {new Stack(1, 60)};
        ItemStack[] target = {new Stack(1, 60), null};
        assertEquals(60, SaddlebagTransfer.move(source, 0, target, false));
        assertNull(source[0]);
        assertEquals(64, target[0].getAmount());
        assertEquals(56, target[1].getAmount());
    }

    @Test
    void fullDestinationAndIncompatibleStacksLeaveSourceIntact() {
        ItemStack[] source = {new Stack(1, 60)};
        ItemStack[] target = {new Stack(2, 20), new Stack(1, 64)};
        assertEquals(0, SaddlebagTransfer.move(source, 0, target, false));
        assertEquals(60, source[0].getAmount());
        assertEquals(20, target[0].getAmount());
    }

    @Test
    void halfTransferRoundsUpAndPartialFitConservesCounts() {
        ItemStack[] source = {new Stack(1, 17)};
        ItemStack[] target = {null};
        assertEquals(9, SaddlebagTransfer.move(source, 0, target, true));
        assertEquals(8, source[0].getAmount());
        assertEquals(9, target[0].getAmount());
        target[0].setAmount(62);
        assertEquals(2, SaddlebagTransfer.move(source, 0, target, false));
        assertEquals(6, source[0].getAmount());
        assertEquals(64, target[0].getAmount());
    }

    @Test
    void planningCopiesNeverMutateLiveInventoryAndEmptySlotIsNoOp() {
        ItemStack[] live = {new Stack(1, 10), null};
        ItemStack[] copy = SaddlebagTransfer.copy(live);
        assertEquals(0, SaddlebagTransfer.move(copy, 1, new ItemStack[1], false));
        SaddlebagTransfer.move(copy, 0, new ItemStack[1], false);
        assertEquals(10, live[0].getAmount());
    }

    private static final class Stack extends ItemStack {
        private final int kind;
        private int count;
        Stack(int kind, int count) { this.kind = kind; this.count = count; }
        @Override public int getAmount() { return count; }
        @Override public void setAmount(int count) { this.count = count; }
        @Override public int getMaxStackSize() { return 64; }
        @Override public boolean isEmpty() { return count == 0; }
        @Override public boolean isSimilar(ItemStack other) { return other instanceof Stack s && kind == s.kind; }
        @Override public Stack clone() { return new Stack(kind, count); }
    }
}
