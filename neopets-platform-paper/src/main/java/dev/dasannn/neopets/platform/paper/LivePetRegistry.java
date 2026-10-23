package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry from active domain pet IDs to their live Bukkit entity UUIDs. */
public final class LivePetRegistry {
    private final ConcurrentHashMap<PetId, UUID> entities = new ConcurrentHashMap<>();

    /** Associates a pet with its current entity, returning the previous entity UUID if present. */
    public Optional<UUID> register(PetId petId, UUID entityId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entityId, "entityId");
        return Optional.ofNullable(entities.put(petId, entityId));
    }

    /** Returns the current entity UUID for a pet. */
    public Optional<UUID> entityId(PetId petId) {
        return Optional.ofNullable(entities.get(Objects.requireNonNull(petId, "petId")));
    }

    /** Removes and returns the current entity UUID for a pet. */
    public Optional<UUID> unregister(PetId petId) {
        return Optional.ofNullable(entities.remove(Objects.requireNonNull(petId, "petId")));
    }

    /** Removes a mapping only if it still points at the supplied entity UUID. */
    public boolean unregister(PetId petId, UUID entityId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entityId, "entityId");
        return entities.remove(petId, entityId);
    }

    /** Returns an immutable point-in-time snapshot suitable for one ticker pass. */
    public Map<PetId, UUID> snapshot() {
        return Map.copyOf(entities);
    }

    /** Returns the number of registered live pets. */
    public int size() {
        return entities.size();
    }
}
