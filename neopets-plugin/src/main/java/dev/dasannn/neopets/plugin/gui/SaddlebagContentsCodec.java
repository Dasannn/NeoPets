package dev.dasannn.neopets.plugin.gui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/** Versioned binary codec for exactly 27 independently serialized Bukkit item slots. */
public final class SaddlebagContentsCodec {
    public static final int SLOT_COUNT = 27;
    private static final int MAGIC = 0x4E_50_53_42; // NPSB
    private static final int VERSION = 1;
    private static final int MAX_ITEM_BYTES = 16 * 1024 * 1024;
    private final ItemSerialization items;

    /** Creates the production codec backed by Paper's lossless item byte serialization. */
    public SaddlebagContentsCodec() {
        this(new ItemSerialization() {
            @Override public byte[] serialize(ItemStack item) { return item.serializeAsBytes(); }
            @Override public ItemStack deserialize(byte[] bytes) { return ItemStack.deserializeBytes(bytes); }
        });
    }

    SaddlebagContentsCodec(ItemSerialization items) {
        this.items = Objects.requireNonNull(items, "items");
    }

    /** Encodes a defensive point-in-time snapshot. */
    public byte[] encode(ItemStack[] contents) {
        Objects.requireNonNull(contents, "contents");
        if (contents.length != SLOT_COUNT) {
            throw new IllegalArgumentException("saddlebag must contain exactly " + SLOT_COUNT + " slots");
        }
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeInt(SLOT_COUNT);
                for (ItemStack item : contents) {
                    if (item == null || item.isEmpty()) {
                        output.writeInt(-1);
                    } else {
                        byte[] serialized = items.serialize(item.clone());
                        if (serialized.length > MAX_ITEM_BYTES) {
                            throw new IllegalArgumentException("serialized item exceeds maximum size");
                        }
                        output.writeInt(serialized.length);
                        output.write(serialized);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    /** Decodes a validated snapshot, rejecting unknown formats and trailing bytes. */
    public ItemStack[] decode(byte[] serialized) {
        Objects.requireNonNull(serialized, "serialized");
        try (var input = new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(serialized, serialized.length)))) {
            if (input.readInt() != MAGIC) throw new IllegalArgumentException("invalid saddlebag blob magic");
            int version = input.readInt();
            if (version != VERSION) throw new IllegalArgumentException("unsupported saddlebag blob version " + version);
            if (input.readInt() != SLOT_COUNT) throw new IllegalArgumentException("invalid saddlebag slot count");
            var contents = new ItemStack[SLOT_COUNT];
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                int length = input.readInt();
                if (length == -1) continue;
                if (length < 0 || length > MAX_ITEM_BYTES || length > input.available()) {
                    throw new IllegalArgumentException("invalid serialized item length at slot " + slot);
                }
                contents[slot] = items.deserialize(input.readNBytes(length));
            }
            if (input.available() != 0) throw new IllegalArgumentException("trailing saddlebag blob data");
            return contents;
        } catch (IOException failure) {
            throw new IllegalArgumentException("invalid saddlebag blob", failure);
        }
    }

    /** Returns the canonical empty 27-slot representation. */
    public byte[] empty() {
        return encode(new ItemStack[SLOT_COUNT]);
    }

    interface ItemSerialization {
        byte[] serialize(ItemStack item);
        ItemStack deserialize(byte[] bytes);
    }
}
