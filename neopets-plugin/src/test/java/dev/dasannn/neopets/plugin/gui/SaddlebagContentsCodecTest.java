package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class SaddlebagContentsCodecTest {
    @Test
    void roundTripsEachSlotUsingPaperItemSerialization() {
        var codec = fakeCodec();
        var contents = new ItemStack[SaddlebagContentsCodec.SLOT_COUNT];
        contents[0] = new FakeItem(17);
        contents[13] = new FakeItem(64);
        contents[26] = new FakeItem(1);

        ItemStack[] decoded = codec.decode(codec.encode(contents));

        assertEquals(contents[0], decoded[0]);
        assertEquals(contents[13], decoded[13]);
        assertEquals(contents[26], decoded[26]);
        assertNull(decoded[1]);
    }

    @Test
    void rejectsWrongSlotCountsAndCorruptBlobs() {
        var codec = fakeCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new ItemStack[26]));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[] {1, 2, 3, 4}));
    }

    private static SaddlebagContentsCodec fakeCodec() {
        return new SaddlebagContentsCodec(new SaddlebagContentsCodec.ItemSerialization() {
            @Override public byte[] serialize(ItemStack item) {
                return new byte[] {(byte) ((FakeItem) item).value};
            }
            @Override public ItemStack deserialize(byte[] bytes) {
                return new FakeItem(Byte.toUnsignedInt(bytes[0]));
            }
        });
    }

    private static final class FakeItem extends ItemStack {
        private final int value;
        private FakeItem(int value) { this.value = value; }
        @Override public boolean isEmpty() { return false; }
        @Override public FakeItem clone() { return new FakeItem(value); }
        @Override public boolean equals(Object other) {
            return other instanceof FakeItem item && value == item.value;
        }
        @Override public int hashCode() { return value; }
    }
}
