package dev.dasannn.neopets.plugin.listener;

import org.bukkit.NamespacedKey;

/** Persistent identity keys written to every NeoPets soul item. */
public final class SoulItemKeys {
    /** Unique, single-use token identifier. */
    public static final NamespacedKey TOKEN_ID = new NamespacedKey("neopets", "soul_token_id");
    /** Pet instance represented by the soul. */
    public static final NamespacedKey PET_ID = new NamespacedKey("neopets", "pet_id");
    /** Player allowed to use the soul. */
    public static final NamespacedKey OWNER = new NamespacedKey("neopets", "owner");

    private SoulItemKeys() {
    }
}
