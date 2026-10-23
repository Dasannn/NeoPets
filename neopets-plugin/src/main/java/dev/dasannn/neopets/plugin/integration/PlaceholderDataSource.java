package dev.dasannn.neopets.plugin.integration;

import java.util.Optional;
import java.util.UUID;

/** Supplies already-loaded immutable data for PlaceholderAPI requests. */
@FunctionalInterface
public interface PlaceholderDataSource {
    /** Returns a snapshot only while the player's complete NeoPets data is loaded. */
    Optional<PlaceholderSnapshot> snapshot(UUID playerId);
}
