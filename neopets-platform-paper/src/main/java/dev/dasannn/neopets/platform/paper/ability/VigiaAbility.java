package dev.dasannn.neopets.platform.paper.ability;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Parrot alert ability registered as the {@code lookout} custom effect.
 *
 * <p>Paper 26.3 has no supported per-player glowing API, so the vanilla glowing effect is visible to every nearby
 * player. Each effect expires on its own; deactivation deliberately does not remove it because the target may have
 * already been glowing from another source.</p>
 */
public final class VigiaAbility implements Ability {
    /** Skill-tree custom effect identifier shipped for the parrot. */
    public static final String ID = "lookout";

    private final Supplier<Settings> settings;
    private final AlertEffect alertEffect;
    private final Map<UUID, Set<UUID>> previouslyVisible = new HashMap<>();

    /** Creates a dynamically configured Vigía ability. */
    public VigiaAbility(Supplier<Settings> settings) {
        this(settings, VigiaAbility::alertAndGlow);
    }

    VigiaAbility(Supplier<Settings> settings, AlertEffect alertEffect) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.alertEffect = Objects.requireNonNull(alertEffect, "alertEffect");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onActivate(LivingEntity pet, Player owner, int level) {
        previouslyVisible.remove(pet.getUniqueId());
    }

    @Override
    public void onDeactivate(LivingEntity pet, Player owner, int level) {
        previouslyVisible.remove(pet.getUniqueId());
    }

    @Override
    public void tick(AbilityContext context) {
        Settings configured = Objects.requireNonNull(settings.get(), "vigia settings");
        if (!context.claimCooldown(configured.cooldown())) {
            return;
        }
        var origin = context.owner().getLocation();
        var current = AbilityTargetSelector.selectHostileEntities(
                        context.owner().getNearbyEntities(
                                configured.radius(), configured.radius(), configured.radius()),
                        context.owner().getUniqueId())
                .stream()
                .filter(target -> target.getWorld().equals(origin.getWorld()))
                .filter(target -> target.getLocation().distanceSquared(origin)
                        <= configured.radius() * configured.radius())
                .toList();
        Set<UUID> currentIds = current.stream()
                .map(LivingEntity::getUniqueId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<UUID> previous = previouslyVisible.put(context.pet().getUniqueId(), currentIds);
        Set<UUID> known = previous == null ? Set.of() : previous;
        var newlyVisible = current.stream()
                .filter(target -> !known.contains(target.getUniqueId()))
                .toList();
        if (newlyVisible.isEmpty()) {
            return;
        }
        alertEffect.apply(context.owner(), newlyVisible, configured.duration());
    }

    private static void alertAndGlow(Player owner, List<LivingEntity> targets, Duration duration) {
        owner.playSound(owner, Sound.BLOCK_NOTE_BLOCK_BELL, SoundCategory.PLAYERS, 1.0F, 1.4F);
        PotionEffect glowing = new PotionEffect(
                PotionEffectType.GLOWING, durationTicks(duration), 0, true, false, false);
        targets.forEach(target -> target.addPotionEffect(glowing));
    }

    private static int durationTicks(Duration duration) {
        return Math.toIntExact(Math.max(1L, duration.toMillis() / 50L));
    }

    @FunctionalInterface
    interface AlertEffect {
        void apply(Player owner, List<LivingEntity> targets, Duration duration);
    }

    /** Validated runtime settings for Vigía. */
    public record Settings(Duration cooldown, double radius, Duration duration) {
        public Settings {
            Objects.requireNonNull(cooldown, "cooldown");
            Objects.requireNonNull(duration, "duration");
            if (cooldown.isZero() || cooldown.isNegative()) {
                throw new IllegalArgumentException("cooldown must be positive");
            }
            if (!Double.isFinite(radius) || radius <= 0) {
                throw new IllegalArgumentException("radius must be finite and positive");
            }
            if (duration.isZero() || duration.isNegative()
                    || duration.compareTo(Duration.ofMillis(Integer.MAX_VALUE * 50L)) > 0) {
                throw new IllegalArgumentException("duration must be positive and fit in effect ticks");
            }
        }
    }
}
