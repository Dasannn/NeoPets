package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Parsed PDC identity of a soul item presented to the revive command. */
public record SoulItemIdentity(UUID tokenId, PetId petId, PlayerId ownerId) {
    public SoulItemIdentity {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
    }

    /** Distinguishes an ordinary/empty hand from a malformed tagged soul item. */
    public sealed interface ReadResult
            permits ReadResult.Missing, ReadResult.Invalid, ReadResult.Found {
        /** The item has no NeoPets soul marker. */
        record Missing() implements ReadResult {
        }

        /** The item is marked as a soul but its complete UUID identity cannot be read. */
        record Invalid() implements ReadResult {
        }

        /** The item carries a complete syntactically valid identity. */
        record Found(SoulItemIdentity identity) implements ReadResult {
            public Found {
                Objects.requireNonNull(identity, "identity");
            }
        }
    }

    /** Reads a soul identity using only the dedicated PDC keys. */
    public static ReadResult read(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) {
            return new ReadResult.Missing();
        }
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        if (!data.has(SoulItemKeys.TOKEN_ID)) {
            return new ReadResult.Missing();
        }
        if (!data.has(SoulItemKeys.TOKEN_ID, PersistentDataType.STRING)
                || !data.has(SoulItemKeys.PET_ID, PersistentDataType.STRING)
                || !data.has(SoulItemKeys.OWNER, PersistentDataType.STRING)) {
            return new ReadResult.Invalid();
        }
        return parse(data.get(SoulItemKeys.TOKEN_ID, PersistentDataType.STRING),
                data.get(SoulItemKeys.PET_ID, PersistentDataType.STRING),
                data.get(SoulItemKeys.OWNER, PersistentDataType.STRING));
    }

    /** Returns whether an item carries the soul marker, regardless of malformed secondary data. */
    public static boolean isSoul(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                .has(SoulItemKeys.TOKEN_ID);
    }

    static ReadResult parse(String tokenId, String petId, String ownerId) {
        if (tokenId == null) {
            return new ReadResult.Missing();
        }
        if (petId == null || ownerId == null) {
            return new ReadResult.Invalid();
        }
        try {
            return new ReadResult.Found(new SoulItemIdentity(
                    UUID.fromString(tokenId),
                    new PetId(UUID.fromString(petId)),
                    new PlayerId(UUID.fromString(ownerId))));
        } catch (IllegalArgumentException exception) {
            return new ReadResult.Invalid();
        }
    }
}
