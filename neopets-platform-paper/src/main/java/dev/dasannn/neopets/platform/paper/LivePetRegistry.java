package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry from active domain pet IDs to their live Bukkit entity UUIDs. */
public final class LivePetRegistry {
    private final ConcurrentHashMap<PetId, UUID> entities = new ConcurrentHashMap<>();
    private final Set<PetId> pendingRecoveries = ConcurrentHashMap.newKeySet();

    /** Associates a pet with its current entity, returning the previous entity UUID if present. */
    public synchronized Optional<UUID> register(PetId petId, UUID entityId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entityId, "entityId");
        pendingRecoveries.remove(petId);
        return Optional.ofNullable(entities.put(petId, entityId));
    }

    /** Returns the current entity UUID for a pet. */
    public Optional<UUID> entityId(PetId petId) {
        return Optional.ofNullable(entities.get(Objects.requireNonNull(petId, "petId")));
    }

    /**
     * Removes all live and recovery state for a pet.
     *
     * <p>This is the explicit-despawn operation. A pending unload recovery will not recreate the pet after this
     * method returns.</p>
     */
    public synchronized Optional<UUID> unregister(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        pendingRecoveries.remove(petId);
        return Optional.ofNullable(entities.remove(petId));
    }

    /** Removes a mapping only if it still points at the supplied entity UUID. */
    public boolean unregister(PetId petId, UUID entityId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entityId, "entityId");
        return entities.remove(petId, entityId);
    }

    /** Moves the current entity association into recoverable state if it still matches the unloading entity. */
    public synchronized boolean beginRecovery(PetId petId, UUID entityId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entityId, "entityId");
        if (!entities.remove(petId, entityId)) {
            return false;
        }
        pendingRecoveries.add(petId);
        return true;
    }

    /** Returns whether a pet still needs an entity recreated after an unload. */
    public boolean isRecoveryPending(PetId petId) {
        return pendingRecoveries.contains(Objects.requireNonNull(petId, "petId"));
    }

    /** Returns an immutable point-in-time snapshot of pets awaiting unload recovery. */
    public Set<PetId> pendingRecoveriesSnapshot() {
        return Set.copyOf(pendingRecoveries);
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
