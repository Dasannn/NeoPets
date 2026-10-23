package dev.dasannn.neopets.platform.paper;

import org.bukkit.NamespacedKey;

/** Persistent data keys attached to spawned NeoPets entities. */
public final class PetKeys {
    /** Unique {@code PetId} of the spawned pet. */
    public static final NamespacedKey PET_ID = new NamespacedKey("neopets", "pet_id");

    /** UUID of the player who owns the pet. */
    public static final NamespacedKey OWNER = new NamespacedKey("neopets", "owner");

    /** Configured {@code VariantId} used to spawn the pet. */
    public static final NamespacedKey VARIANT_ID = new NamespacedKey("neopets", "variant_id");

    /** UUID of the recipient while this entity represents an accepted courier delivery. */
    public static final NamespacedKey COURIER_RECIPIENT = new NamespacedKey("neopets", "courier_recipient");

    private PetKeys() {
    }
}
