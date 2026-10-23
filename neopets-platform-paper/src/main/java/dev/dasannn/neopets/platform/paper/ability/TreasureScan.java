package dev.dasannn.neopets.platform.paper.ability;

import java.util.ArrayList;
import java.util.List;
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
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private final List<Position> targets = new ArrayList<>();
    private int chunkX;
    private int chunkZ;
    private int x;
    private int y;
    private int z;
    private int scanned;
    private boolean exhausted;
    private ChunkSnapshot snapshot;

    TreasureScan(World world, int originX, int originY, int originZ,
                 double radius, BuscatesorosAbility.Settings settings) {
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.radius = radius;
        this.settings = settings;
        minX = (int) Math.ceil(originX - radius);
        maxX = (int) Math.floor(originX + radius);
        minY = (int) Math.max(world.getMinHeight(), Math.ceil(originY - radius));
        maxY = (int) Math.min(world.getMaxHeight() - 1, Math.floor(originY + radius));
        minZ = (int) Math.ceil(originZ - radius);
        maxZ = (int) Math.floor(originZ + radius);
        chunkX = minX >> 4;
        chunkZ = minZ >> 4;
        exhausted = minY > maxY;
    }

    /** Takes at most one chunk snapshot per call, retaining its cursor across shared ticker calls. */
    void advance(int workLimit) {
        int work = 0;
        boolean captured = false;
        if (snapshot != null && !world.isChunkLoaded(chunkX, chunkZ)) {
            nextChunk();
        }
        while (work < workLimit && !complete()) {
            if (snapshot == null) {
                if (captured) {
                    return;
                }
                work++;
                scanned++;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    nextChunk();
                    continue;
                }
                // Main-thread loaded check above prevents getChunkAt from loading a chunk.
                snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false, false);
                captured = true;
                x = Math.max(minX, chunkX << 4);
                y = minY;
                z = Math.max(minZ, chunkZ << 4);
                continue;
            }
            work++;
            scanned++;
            var position = new Position(x, y, z);
            if (TreasureTargetSelector.withinRadius(x, y, z, originX, originY, originZ, radius)) {
                inspect(position);
            }
            nextPosition();
        }
        if (complete()) {
            snapshot = null;
        }
    }

    private void inspect(Position position) {
        Material material = snapshot.getBlockType(x & 15, y, z & 15);
        boolean lootableBlock = false;
        boolean hasLootTable = false;
        if (material != Material.AIR && material != Material.CAVE_AIR && material != Material.VOID_AIR
                && !settings.oreMaterials().contains(material)) {
            // No tile-entity arrays or eager state snapshots: inspect only this budgeted position.
            var state = world.getBlockAt(x, y, z).getState(false);
            if (state instanceof Lootable lootable) {
                lootableBlock = true;
                hasLootTable = lootable.hasLootTable();
            }
        }
        if (TreasureTargetSelector.isTreasure(lootableBlock, hasLootTable, material, settings.oreMaterials())) {
            targets.add(position);
        }
    }

    private void nextPosition() {
        if (++z <= Math.min(maxZ, (chunkZ << 4) + 15)) {
            return;
        }
        z = Math.max(minZ, chunkZ << 4);
        if (++y <= maxY) {
            return;
        }
        y = minY;
        if (++x > Math.min(maxX, (chunkX << 4) + 15)) {
            nextChunk();
        }
    }

    private void nextChunk() {
        snapshot = null;
        if (++chunkZ > (maxZ >> 4)) {
            chunkZ = minZ >> 4;
            if (++chunkX > (maxX >> 4)) {
                exhausted = true;
            }
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
