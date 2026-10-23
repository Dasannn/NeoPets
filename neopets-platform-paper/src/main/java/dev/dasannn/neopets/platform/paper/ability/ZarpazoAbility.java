package dev.dasannn.neopets.platform.paper.ability;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Polar-bear area attack registered as the {@code claw_swipe} custom effect. */
public final class ZarpazoAbility implements Ability {
    /** Skill-tree custom effect identifier shipped for the polar bear. */
    public static final String ID = "claw_swipe";

    private final Supplier<Settings> settings;
    private final BiConsumer<LivingEntity, Location> feedback;

    /** Creates a dynamically configured Zarpazo ability. */
    public ZarpazoAbility(Supplier<Settings> settings) {
        this(settings, ZarpazoAbility::playFeedback);
    }

    ZarpazoAbility(Supplier<Settings> settings, BiConsumer<LivingEntity, Location> feedback) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onActivate(LivingEntity pet, Player owner, int level) {
    }

    @Override
    public void onDeactivate(LivingEntity pet, Player owner, int level) {
    }

    @Override
    public void tick(AbilityContext context) {
        Settings configured = Objects.requireNonNull(settings.get(), "zarpazo settings");
        if (!context.claimCooldown(configured.cooldown())) {
            return;
        }
        Location origin = context.pet().getLocation();
        var targets = AbilityTargetSelector.selectHostileEntities(
                        context.pet().getNearbyEntities(
                                configured.radius(), configured.radius(), configured.radius()),
                        context.owner().getUniqueId())
                .stream()
                .filter(target -> target.getWorld().equals(origin.getWorld()))
                .filter(target -> target.getLocation().distanceSquared(origin)
                        <= configured.radius() * configured.radius())
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        targets.forEach(target -> target.damage(configured.damage(), context.pet()));
        feedback.accept(context.pet(), origin);
    }

    private static void playFeedback(LivingEntity pet, Location origin) {
        pet.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK, origin.clone().add(0, 1, 0), 8, 0.8, 0.25, 0.8, 0.0);
        pet.getWorld().playSound(
                origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.NEUTRAL, 1.0F, 0.8F);
    }

    /** Validated runtime settings for Zarpazo. */
    public record Settings(Duration cooldown, double radius, double damage) {
        public Settings {
            Objects.requireNonNull(cooldown, "cooldown");
            if (cooldown.isZero() || cooldown.isNegative()) {
                throw new IllegalArgumentException("cooldown must be positive");
            }
            if (!Double.isFinite(radius) || radius <= 0) {
                throw new IllegalArgumentException("radius must be finite and positive");
            }
            if (!Double.isFinite(damage) || damage <= 0) {
                throw new IllegalArgumentException("damage must be finite and positive");
            }
        }
    }
}
