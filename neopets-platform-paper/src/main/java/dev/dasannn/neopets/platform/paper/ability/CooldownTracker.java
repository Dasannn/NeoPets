package dev.dasannn.neopets.platform.paper.ability;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Shared in-memory tracker for per-pet, per-ability cooldowns. */
public final class CooldownTracker {
    private final Map<Key, Instant> nextUses = new HashMap<>();

    /** Claims a ready cooldown and advances its next-use instant. */
    public boolean claim(PetId petId, String abilityId, Duration cooldown, Instant now) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(now, "now");
        if (abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId must not be blank");
        }
        if (cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        var key = new Key(petId, abilityId);
        Instant nextUse = nextUses.get(key);
        if (nextUse != null && now.isBefore(nextUse)) {
            return false;
        }
        nextUses.put(key, now.plus(cooldown));
        return true;
    }

    /** Clears one ability cooldown, normally when that ability is deactivated. */
    public void clear(PetId petId, String abilityId) {
        nextUses.remove(new Key(
                Objects.requireNonNull(petId, "petId"),
                Objects.requireNonNull(abilityId, "abilityId")));
    }

    /** Clears every cooldown for one pet. */
    public void clear(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        nextUses.keySet().removeIf(key -> key.petId().equals(petId));
    }

    /** Clears all tracked cooldowns. */
    public void clear() {
        nextUses.clear();
    }

    private record Key(PetId petId, String abilityId) {
    }
}
