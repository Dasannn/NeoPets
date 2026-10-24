package dev.dasannn.neopets.platform.paper.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.loot.Lootable;

/** Incremental snapshot traversal; every chunk probe and position consumes the activation budget. */
final class TreasureScan {
    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final double radius;
    private final BuscatesorosAbility.Settings settings;
    private final int minY;
    private final int maxY;
    private final int maxShell;
    // Null entries remember unavailable chunks, so a chunk is never captured twice.
    // Only chunks intersecting the bounded scan cube can enter this cache.
    private final Map<ChunkPosition, ChunkSnapshot> snapshots = new HashMap<>();
    private final List<Position> targets = new ArrayList<>();
    private int shell;
    private int dx;
    private int dy;
    private int dz;
    private int scanned;
    private boolean exhausted;

    TreasureScan(World world, int originX, int originY, int originZ,
                 double radius, BuscatesorosAbility.Settings settings) {
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.radius = radius;
        this.settings = settings;
        minY = (int) Math.max(world.getMinHeight(), Math.ceil(originY - radius));
        maxY = (int) Math.min(world.getMaxHeight() - 1, Math.floor(originY + radius));
        maxShell = (int) Math.floor(radius);
        shell = Math.max(0, Math.max(minY - originY, originY - maxY));
        startShell();
        exhausted = minY > maxY || shell > maxShell;
    }

    /** Visits Chebyshev distance shells, with at most one new chunk snapshot per call. */
    void advance(int workLimit) {
        int work = 0;
        boolean captured = false;
        while (work < workLimit && !complete()) {
            int blockX = originX + dx;
            int blockY = originY + dy;
            int blockZ = originZ + dz;
            var chunk = new ChunkPosition(blockX >> 4, blockZ >> 4);
            if (!snapshots.containsKey(chunk)) {
                if (captured) {
                    return;
                }
                work++;
                scanned++;
                ChunkSnapshot snapshot = null;
                if (world.isChunkLoaded(chunk.x(), chunk.z())) {
                    // The main-thread loaded check prevents getChunkAt from loading a chunk.
                    snapshot = world.getChunkAt(chunk.x(), chunk.z())
                            .getChunkSnapshot(false, false, false, false);
                    captured = true;
                }
                snapshots.put(chunk, snapshot);
                continue;
            }
            work++;
            scanned++;
            ChunkSnapshot snapshot = snapshots.get(chunk);
            if (snapshot != null) {
                if (!world.isChunkLoaded(chunk.x(), chunk.z())) {
                    snapshots.put(chunk, null);
                } else if (TreasureTargetSelector.withinRadius(blockX, blockY, blockZ,
                        originX, originY, originZ, radius)) {
                    inspect(new Position(blockX, blockY, blockZ), snapshot);
                }
            }
            nextPosition();
        }
        if (complete()) {
            snapshots.clear();
        }
    }

    private void startShell() {
        dx = -shell;
        dy = Math.max(-shell, minY - originY);
        dz = -shell;
    }

    private void nextPosition() {
        // On interior rows only the two Z faces belong to this shell.
        // Jump across the interior instead of doing unbudgeted cube traversal.
        if (Math.abs(dx) < shell && Math.abs(dy) < shell && dz == -shell) {
            dz = shell;
        } else {
            dz++;
        }
        if (dz > shell) {
            dz = -shell;
            if (++dy > Math.min(shell, maxY - originY)) {
                dy = Math.max(-shell, minY - originY);
                if (++dx > shell) {
                    if (++shell > maxShell) {
                        exhausted = true;
                    } else {
                        startShell();
                    }
                }
            }
        }
    }

    private void inspect(Position position, ChunkSnapshot snapshot) {
        Material material = snapshot.getBlockType(position.x() & 15, position.y(), position.z() & 15);
        boolean lootableBlock = false;
        boolean hasLootTable = false;
        if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR
                && !settings.oreMaterials().contains(material)) {
            // No tile-entity arrays or eager state snapshots: inspect only this budgeted position.
            var state = world.getBlockAt(position.x(), position.y(), position.z()).getState(false);
            if (state instanceof Lootable lootable) {
                lootableBlock = true;
                hasLootTable = lootable.hasLootTable();
            }
        }
        if (TreasureTargetSelector.isTreasure(lootableBlock, hasLootTable, material, settings.oreMaterials())) {
            targets.add(position);
        }
    }

    boolean complete() {
        return exhausted || scanned >= settings.maxScanBlocks() || targets.size() >= settings.maxTargets();
    }

    int scannedBlocks() {
        return scanned;
    }

    List<Position> targets() {
        return List.copyOf(targets);
    }

    private record ChunkPosition(int x, int z) {
    }

    record Position(int x, int y, int z) {
    }
}
