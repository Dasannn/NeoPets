package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Removes loaded, PDC-tagged pet entities whose IDs are not active in the domain. */
public final class OrphanCleaner {
    /** Removes orphans from all currently loaded worlds and returns the number removed. */
    public int cleanLoadedEntities(Server server, Set<PetId> activePetIds) {
        Objects.requireNonNull(server, "server");
        var activeIds = toStrings(activePetIds);
        var removed = 0;
        for (var world : server.getWorlds()) {
            removed += clean(world.getEntities(), activeIds);
        }
        return removed;
    }

    /** Removes orphans from one loaded chunk and returns the number removed. */
    public int cleanChunk(Chunk chunk, Set<PetId> activePetIds) {
        Objects.requireNonNull(chunk, "chunk");
        return clean(java.util.List.of(chunk.getEntities()), toStrings(activePetIds));
    }

    /** Removes orphans from the supplied loaded entities and returns the number removed. */
    public int cleanEntities(Iterable<? extends Entity> entities, Set<PetId> activePetIds) {
        return clean(Objects.requireNonNull(entities, "entities"), toStrings(activePetIds));
    }

    private static int clean(Iterable<? extends Entity> entities, Set<String> activeIds) {
        var removed = 0;
        for (var entity : entities) {
            var data = entity.getPersistentDataContainer();
            if (!data.has(PetKeys.PET_ID)) {
                continue;
            }
            var petId = data.get(PetKeys.PET_ID, PersistentDataType.STRING);
            if (petId == null || !activeIds.contains(petId)) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }

    private static Set<String> toStrings(Set<PetId> activePetIds) {
        Objects.requireNonNull(activePetIds, "activePetIds");
        var activeIds = new HashSet<String>(activePetIds.size());
        for (var petId : activePetIds) {
            activeIds.add(Objects.requireNonNull(petId, "activePetIds element").toString());
        }
        return Set.copyOf(activeIds);
    }
}
