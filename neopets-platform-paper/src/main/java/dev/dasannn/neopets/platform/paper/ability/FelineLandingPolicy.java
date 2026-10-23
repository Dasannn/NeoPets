package dev.dasannn.neopets.platform.paper.ability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Constant-time owner lookup for live cats with active feline landing. */
public final class FelineLandingPolicy {
    private final BooleanSupplier enabled;

    /** Creates an enabled policy. */
    public FelineLandingPolicy() {
        this(() -> true);
    }

    /** Creates a policy that consults the current configuration on every damage event. */
    public FelineLandingPolicy(BooleanSupplier enabled) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    private final Map<UUID, Set<UUID>> activeCatsByOwner = new HashMap<>();
    private final Map<UUID, UUID> ownersByCat = new HashMap<>();

    /** Records a summoned live cat whose ability is active. */
    public void activate(UUID ownerId, UUID petEntityId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(petEntityId, "petEntityId");
        UUID previousOwner = ownersByCat.put(petEntityId, ownerId);
        if (previousOwner != null && !previousOwner.equals(ownerId)) {
            remove(previousOwner, petEntityId);
        }
        activeCatsByOwner.computeIfAbsent(ownerId, ignored -> new HashSet<>()).add(petEntityId);
    }

    /** Removes a dismissed, dead, replaced, or deactivated cat. */
    public void deactivate(UUID ownerId, UUID petEntityId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(petEntityId, "petEntityId");
        ownersByCat.remove(petEntityId, ownerId);
        remove(ownerId, petEntityId);
    }

    /** Removes a cat immediately when its live entity dies. */
    public void deactivatePet(UUID petEntityId) {
        Objects.requireNonNull(petEntityId, "petEntityId");
        UUID ownerId = ownersByCat.remove(petEntityId);
        if (ownerId != null) {
            remove(ownerId, petEntityId);
        }
    }

    private void remove(UUID ownerId, UUID petEntityId) {
        Set<UUID> cats = activeCatsByOwner.get(ownerId);
        if (cats == null) {
            return;
        }
        cats.remove(petEntityId);
        if (cats.isEmpty()) {
            activeCatsByOwner.remove(ownerId);
        }
    }

    /** Returns whether the owner currently has at least one qualifying cat. */
    public boolean preventsFallDamage(UUID ownerId) {
        Set<UUID> cats = activeCatsByOwner.get(Objects.requireNonNull(ownerId, "ownerId"));
        return enabled.getAsBoolean() && cats != null && !cats.isEmpty();
    }
}
