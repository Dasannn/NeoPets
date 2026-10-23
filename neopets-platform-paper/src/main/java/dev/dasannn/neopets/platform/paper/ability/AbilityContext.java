package dev.dasannn.neopets.platform.paper.ability;

import dev.dasannn.neopets.core.model.PetId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Immutable context supplied to an ability by the shared pet ticker.
 *
 * <p>The detection-radius bonus is the current additive modifier applied by the dog's {@code scent} branch. It is
 * exposed explicitly because custom abilities must not rediscover skill-tree state or duplicate effect rules.</p>
 */
public record AbilityContext(
        PetId petId,
        LivingEntity pet,
        Player owner,
        int level,
        Instant now,
        CooldownTracker cooldowns,
        double detectionRadiusBonus,
        String abilityId
) {
    public AbilityContext {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(abilityId, "abilityId");
        if (level < 1) {
            throw new IllegalArgumentException("level must be positive");
        }
        if (!Double.isFinite(detectionRadiusBonus) || detectionRadiusBonus < 0) {
            throw new IllegalArgumentException("detectionRadiusBonus must be finite and non-negative");
        }
    }

    /** Claims this ability's per-pet cooldown if it is ready. */
    public boolean claimCooldown(Duration duration) {
        return cooldowns.claim(petId, abilityId, duration, now);
    }
}
