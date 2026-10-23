package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.platform.paper.OrphanCleaner;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/** Removes stale tagged pet entities whenever a chunk becomes available. */
public final class OrphanCleanupListener implements Listener {
    private final OrphanCleaner cleaner;
    private final LivePetRegistry livePets;

    public OrphanCleanupListener(OrphanCleaner cleaner, LivePetRegistry livePets) {
        this.cleaner = Objects.requireNonNull(cleaner, "cleaner");
        this.livePets = Objects.requireNonNull(livePets, "livePets");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        cleaner.cleanChunk(event.getChunk(), livePets.snapshot().keySet());
    }
}
