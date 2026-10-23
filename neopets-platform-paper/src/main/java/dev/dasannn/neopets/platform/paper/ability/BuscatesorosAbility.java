package dev.dasannn.neopets.platform.paper.ability;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.loot.Lootable;

/** Dog treasure scanner registered as the {@code treasure_hunter} custom effect. */
public final class BuscatesorosAbility implements Ability {
    /** Skill-tree custom effect identifier shipped for dogs. */
    public static final String ID = "treasure_hunter";
    private static final int BLOCKS_PER_TICK = 512;

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
        UUID entityId = context.pet().getUniqueId();
        ScanState scan = scans.get(entityId);
        if (scan != null) {
            scan.emitParticles(context.owner());
            if (!scan.complete()) {
                scan.advance(context.now());
                if (scan.complete() && !scan.targets().isEmpty()) {
                    foundNotifier.accept(context.owner(), scan.targets().size());
                }
            }
            if (scan.expired(context.now())) {
                scans.remove(entityId);
                scan = null;
            }
        }

        Settings configured = Objects.requireNonNull(settings.get(), "buscatesoros settings");
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
            Duration cooldown,
            double baseRadius,
            Duration duration,
            int maxTargets,
            int maxScanBlocks,
            Set<Material> oreMaterials
    ) {
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
        private final int originX;
        private final int originY;
        private final int originZ;
        private final double radiusSquared;
        private final Settings settings;
        private final ArrayDeque<BlockState> containers;
        private final OrePositionCursor orePositions;
        private final Map<BlockKey, Location> targets = new LinkedHashMap<>();
        private int scannedBlocks;
        private Instant markedUntil;

        private ScanState(
                World world,
                int originX,
                int originY,
                int originZ,
                double radius,
                Settings settings,
                ArrayDeque<BlockState> containers,
                OrePositionCursor orePositions
        ) {
            this.world = world;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.radiusSquared = radius * radius;
            this.settings = settings;
            this.containers = containers;
            this.orePositions = orePositions;
        }

        static ScanState start(Location origin, double radius, Settings settings) {
            World world = Objects.requireNonNull(origin.getWorld(), "pet world");
            int originX = origin.getBlockX();
            int originY = origin.getBlockY();
            int originZ = origin.getBlockZ();
            return new ScanState(
                    world,
                    originX,
                    originY,
                    originZ,
                    radius,
                    settings,
                    nearbyTileEntities(world, originX, originZ, radius, settings.maxScanBlocks()),
                    new OrePositionCursor(world, originX, originY, originZ, radius));
        }

        void advance(Instant now) {
            int remainingThisTick = BLOCKS_PER_TICK;
            while (remainingThisTick > 0 && !complete()) {
                if (!containers.isEmpty()) {
                    inspectContainer(containers.removeFirst());
                } else {
                    BlockPosition position = orePositions.next();
                    if (position == null) {
                        finish(now);
                        break;
                    }
                    inspectOre(position);
                }
                scannedBlocks++;
                remainingThisTick--;
                if (targets.size() >= settings.maxTargets()
                        || scannedBlocks >= settings.maxScanBlocks()) {
                    finish(now);
                }
            }
        }

        void emitParticles(Player owner) {
            for (Location target : targets.values()) {
                owner.spawnParticle(Particle.END_ROD, target, 3, 0.2, 0.35, 0.2, 0.01);
            }
        }

        boolean complete() {
            return markedUntil != null;
        }

        boolean expired(Instant now) {
            return markedUntil != null && !now.isBefore(markedUntil);
        }

        List<Location> targets() {
            return List.copyOf(targets.values());
        }

        private void inspectContainer(BlockState state) {
            Location location = state.getLocation();
            if (state instanceof Lootable lootable
                    && lootable.hasLootTable()
                    && withinRadius(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                addTarget(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            }
        }

        private void inspectOre(BlockPosition position) {
            if (!world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
                return;
            }
            Material material = world.getBlockAt(position.x(), position.y(), position.z()).getType();
            if (settings.oreMaterials().contains(material)) {
                addTarget(position.x(), position.y(), position.z());
            }
        }

        private void addTarget(int x, int y, int z) {
            var key = new BlockKey(x, y, z);
            targets.putIfAbsent(key, new Location(world, x + 0.5, y + 0.7, z + 0.5));
        }

        private boolean withinRadius(int x, int y, int z) {
            double dx = x - originX;
            double dy = y - originY;
            double dz = z - originZ;
            return dx * dx + dy * dy + dz * dz <= radiusSquared;
        }

        private void finish(Instant now) {
            markedUntil = now.plus(settings.duration());
            containers.clear();
        }

        private static ArrayDeque<BlockState> nearbyTileEntities(
                World world,
                int originX,
                int originZ,
                double radius,
                int maximum
        ) {
            var states = new ArrayDeque<BlockState>();
            double minimumX = originX - radius;
            double maximumX = originX + radius;
            double minimumZ = originZ - radius;
            double maximumZ = originZ + radius;
            outer:
            for (var chunk : world.getLoadedChunks()) {
                int chunkMinimumX = chunk.getX() << 4;
                int chunkMinimumZ = chunk.getZ() << 4;
                if (chunkMinimumX > maximumX || chunkMinimumX + 15 < minimumX
                        || chunkMinimumZ > maximumZ || chunkMinimumZ + 15 < minimumZ) {
                    continue;
                }
                for (BlockState state : chunk.getTileEntities()) {
                    if (states.size() >= maximum) {
                        break outer;
                    }
                    states.addLast(state);
                }
            }
            return states;
        }

    }

    /** Generates nearest-first positions lazily so large configured caps never allocate a volume-sized list. */
    private static final class OrePositionCursor {
        private final World world;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int maximumShell;
        private final double radiusSquared;
        private int shell;
        private int x;
        private int y;
        private int z;
        private boolean initialized;

        private OrePositionCursor(World world, int originX, int originY, int originZ, double radius) {
            this.world = world;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.maximumShell = (int) Math.ceil(radius);
            this.radiusSquared = radius * radius;
        }

        BlockPosition next() {
            while (shell <= maximumShell) {
                if (!initialized) {
                    x = -shell;
                    y = -shell;
                    z = -shell;
                    initialized = true;
                }
                int candidateX = x;
                int candidateY = y;
                int candidateZ = z;
                int candidateShell = shell;
                advanceCoordinates();
                int actualY = originY + candidateY;
                if (Math.max(Math.max(Math.abs(candidateX), Math.abs(candidateY)), Math.abs(candidateZ))
                        == candidateShell
                        && candidateX * (double) candidateX
                        + candidateY * (double) candidateY
                        + candidateZ * (double) candidateZ <= radiusSquared
                        && actualY >= world.getMinHeight()
                        && actualY < world.getMaxHeight()) {
                    return new BlockPosition(originX + candidateX, actualY, originZ + candidateZ);
                }
            }
            return null;
        }

        private void advanceCoordinates() {
            z++;
            if (z <= shell) {
                return;
            }
            z = -shell;
            y++;
            if (y <= shell) {
                return;
            }
            y = -shell;
            x++;
            if (x <= shell) {
                return;
            }
            shell++;
            initialized = false;
        }
    }

    private record BlockPosition(int x, int y, int z) {
    }

    private record BlockKey(int x, int y, int z) {
    }
}
