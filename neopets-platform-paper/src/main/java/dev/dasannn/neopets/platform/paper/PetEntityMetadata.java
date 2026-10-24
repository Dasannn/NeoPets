package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;

/** Reads the stable PDC identity attached to spawned NeoPets entities. */
public final class PetEntityMetadata {
    private PetEntityMetadata() {
    }

    /** Returns whether the entity carries a NeoPets pet identifier. */
    public static boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(PetKeys.PET_ID, PersistentDataType.STRING);
    }

    /** Returns the valid owner UUID stored on a tagged pet. */
    public static Optional<UUID> ownerId(Entity entity) {
        if (!isPet(entity)) {
            return Optional.empty();
        }
        var value = entity.getPersistentDataContainer().get(PetKeys.OWNER, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** Returns the valid domain pet identifier stored on a tagged entity. */
    public static Optional<PetId> petId(Entity entity) {
        var value = entity.getPersistentDataContainer().get(PetKeys.PET_ID, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PetId(UUID.fromString(value)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
