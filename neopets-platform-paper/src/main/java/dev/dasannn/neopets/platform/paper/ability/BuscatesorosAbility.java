package dev.dasannn.neopets.platform.paper.ability;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Dog treasure scanner registered as the {@code treasure_hunter} custom effect. */
public final class BuscatesorosAbility implements Ability {
    /** Skill-tree custom effect identifier shipped for dogs. */
    public static final String ID = "treasure_hunter";
    private static final int BLOCKS_PER_TICK = 1024;

    private final Supplier<Settings> settings;
    private final BiConsumer<Player, Integer> foundNotifier;
    private final Map<UUID, ScanState> scans = new HashMap<>();

    /** Creates a dynamically configured scanner and localized result notifier. */
    public BuscatesorosAbility(Supplier<Settings> settings, BiConsumer<Player, Integer> foundNotifier) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.foundNotifier = Objects.requireNonNull(foundNotifier, "foundNotifier");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onActivate(LivingEntity pet, Player owner, int level) {
        scans.remove(pet.getUniqueId());
    }

    @Override
    public void onDeactivate(LivingEntity pet, Player owner, int level) {
        scans.remove(pet.getUniqueId());
    }

    @Override
    public void tick(AbilityContext context) {
        Settings configured = Objects.requireNonNull(settings.get(), "buscatesoros settings");
        UUID entityId = context.pet().getUniqueId();
        if (!configured.enabled()) {
            scans.remove(entityId);
            return;
        }
        ScanState scan = scans.get(entityId);
        if (scan != null) {
            if (!scan.complete()) {
                scan.advance(context.now());
                if (scan.complete() && !scan.targets().isEmpty()) {
                    foundNotifier.accept(context.owner(), scan.targets().size());
                }
            }
            if (scan.expired(context.now())) {
                scans.remove(entityId);
                scan = null;
            } else {
                scan.emitParticles(context.owner(), context.now());
            }
        }

        if (scan == null && context.claimCooldown(configured.cooldown())) {
            ScanState started = ScanState.start(
                    context.pet().getLocation(),
                    TreasureTargetSelector.effectiveRadius(
                            configured.baseRadius(), context.detectionRadiusBonus()),
                    configured);
            scans.put(entityId, started);
            started.advance(context.now());
            if (started.complete() && !started.targets().isEmpty()) {
                foundNotifier.accept(context.owner(), started.targets().size());
            }
            started.emitParticles(context.owner(), context.now());
        }
    }

    /** Returns the default material set: every ore block plus ancient debris. */
    public static Set<Material> defaultOreMaterials() {
        return java.util.Arrays.stream(Material.values())
                .filter(material -> !material.name().startsWith("LEGACY_"))
                .filter(material -> material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Validated runtime settings for Buscatesoros. */
    public record Settings(
            boolean enabled,
            Duration cooldown,
            double baseRadius,
            Duration duration,
            int maxTargets,
            int maxScanBlocks,
            Set<Material> oreMaterials
    ) {
        /** Creates enabled settings. */
        public Settings(Duration cooldown, double baseRadius, Duration duration, int maxTargets,
                int maxScanBlocks, Set<Material> oreMaterials) {
            this(true, cooldown, baseRadius, duration, maxTargets, maxScanBlocks, oreMaterials);
        }

        public Settings {
            Objects.requireNonNull(cooldown, "cooldown");
            Objects.requireNonNull(duration, "duration");
            oreMaterials = Set.copyOf(Objects.requireNonNull(oreMaterials, "oreMaterials"));
            if (cooldown.isZero() || cooldown.isNegative()) {
                throw new IllegalArgumentException("cooldown must be positive");
            }
            if (!Double.isFinite(baseRadius) || baseRadius <= 0) {
                throw new IllegalArgumentException("baseRadius must be finite and positive");
            }
            if (duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("duration must be positive");
            }
            if (maxTargets < 1 || maxScanBlocks < 1) {
                throw new IllegalArgumentException("scan and target caps must be positive");
            }
            if (oreMaterials.isEmpty()) {
                throw new IllegalArgumentException("oreMaterials must not be empty");
            }
        }
    }

    private static final class ScanState {
        private final World world;
        private final Settings settings;
        private final TreasureScan scanner;
        private final Map<TreasureScan.Position, Instant> markers = new HashMap<>();
        private int discovered;

        private ScanState(World world, Settings settings, TreasureScan scanner) {
            this.world = world;
            this.settings = settings;
            this.scanner = scanner;
        }

        static ScanState start(Location origin, double radius, Settings settings) {
            World world = Objects.requireNonNull(origin.getWorld(), "pet world");
            return new ScanState(world, settings, new TreasureScan(
                    world, origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(), radius, settings));
        }

        void advance(Instant now) {
            scanner.advance(BLOCKS_PER_TICK);
            List<TreasureScan.Position> targets = scanner.targets();
            for (int index = discovered; index < targets.size(); index++) {
                markers.put(targets.get(index), now.plus(settings.duration()));
            }
            discovered = targets.size();
        }

        void emitParticles(Player owner, Instant now) {
            markers.values().removeIf(expiry -> !now.isBefore(expiry));
            for (var target : markers.keySet()) {
                owner.spawnParticle(Particle.END_ROD,
                        new Location(world, target.x() + 0.5, target.y() + 0.7, target.z() + 0.5),
                        3, 0.2, 0.35, 0.2, 0.01);
            }
        }

        boolean complete() {
            return scanner.complete();
        }

        boolean expired(Instant now) {
            return complete() && markers.values().stream().noneMatch(now::isBefore);
        }

        List<TreasureScan.Position> targets() {
            return scanner.targets();
        }
    }
}
