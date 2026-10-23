package dev.dasannn.neopets.platform.paper.ability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final List<Position> targets = new ArrayList<>();
    // Bound snapshot memory while reusing the four chunks around a typical chunk boundary.
    private final Map<Long, ChunkSnapshot> snapshots = new LinkedHashMap<>(4, 0.75f, true);
    private int shell;
    private int x;
    private int y;
    private int z;
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
        exhausted = minY > maxY;
        startShell();
    }

    /** Takes at most one chunk snapshot per call, visiting concentric cube surfaces near the dog first. */
    void advance(int workLimit) {
        int work = 0;
        boolean captured = false;
        while (work < workLimit && !complete()) {
            int blockX = originX + x;
            int blockY = originY + y;
            int blockZ = originZ + z;
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            ChunkSnapshot snapshot = snapshots.get(key);
            if (snapshot == null && captured) {
                return;
            }
            work++;
            scanned++;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                snapshots.remove(key);
                nextPosition();
                continue;
            }
            if (snapshot == null) {
                // Main-thread loaded check above prevents getChunkAt from loading a chunk.
                snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false, false);
                if (snapshots.size() == 4) {
                    snapshots.remove(snapshots.keySet().iterator().next());
                }
                snapshots.put(key, snapshot);
                captured = true;
                // Snapshot acquisition consumes a work unit separately from inspecting the position.
                continue;
            }
            if (TreasureTargetSelector.withinRadius(blockX, blockY, blockZ,
                    originX, originY, originZ, radius)) {
                inspect(new Position(blockX, blockY, blockZ), snapshot);
            }
            nextPosition();
        }
        if (complete()) {
            snapshots.clear();
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

    private void startShell() {
        x = -shell;
        y = Math.max(-shell, minY - originY);
        z = -shell;
    }

    private void nextPosition() {
        // On interior rows only the two X faces belong to this shell; skip the already visited cube.
        if (Math.abs(y) < shell && Math.abs(z) < shell && x == -shell) {
            x = shell;
            return;
        }
        if (++x <= shell) {
            return;
        }
        x = -shell;
        if (++z <= shell) {
            return;
        }
        z = -shell;
        if (++y <= Math.min(shell, maxY - originY)) {
            return;
        }
        if (++shell > maxShell) {
            exhausted = true;
        } else {
            startShell();
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

    record Position(int x, int y, int z) {
    }
}
