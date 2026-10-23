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
    private final int minY;
    private final int maxY;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private final int maxRing;
    private final List<Position> targets = new ArrayList<>();
    private ChunkSnapshot snapshot;
    private int ring;
    private int chunkOffsetX;
    private int chunkOffsetZ;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;
    private int xIndex;
    private int yIndex;
    private int zIndex;
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
        minChunkX = ((int) Math.ceil(originX - radius)) >> 4;
        maxChunkX = ((int) Math.floor(originX + radius)) >> 4;
        minChunkZ = ((int) Math.ceil(originZ - radius)) >> 4;
        maxChunkZ = ((int) Math.floor(originZ + radius)) >> 4;
        maxRing = Math.max(Math.max((originX >> 4) - minChunkX, maxChunkX - (originX >> 4)),
                Math.max((originZ >> 4) - minChunkZ, maxChunkZ - (originZ >> 4)));
        exhausted = minY > maxY;
    }

    /** Takes at most one snapshot per call and finishes each chunk before visiting the next ring entry. */
    void advance(int workLimit) {
        int work = 0;
        boolean captured = false;
        while (work < workLimit && !complete()) {
            int chunkX = (originX >> 4) + chunkOffsetX;
            int chunkZ = (originZ >> 4) + chunkOffsetZ;
            if (snapshot == null && captured) {
                return;
            }
            work++;
            scanned++;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                nextChunk();
                continue;
            }
            if (snapshot == null) {
                // The main-thread loaded check prevents getChunkAt from loading a chunk.
                snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false, false);
                captured = true;
                minX = Math.max(chunkX << 4, (int) Math.ceil(originX - radius));
                maxX = Math.min((chunkX << 4) + 15, (int) Math.floor(originX + radius));
                minZ = Math.max(chunkZ << 4, (int) Math.ceil(originZ - radius));
                maxZ = Math.min((chunkZ << 4) + 15, (int) Math.floor(originZ + radius));
                continue;
            }
            int blockX = coordinate(xIndex, minX, maxX, originX);
            int blockY = coordinate(yIndex, minY, maxY, originY);
            int blockZ = coordinate(zIndex, minZ, maxZ, originZ);
            if (TreasureTargetSelector.withinRadius(blockX, blockY, blockZ,
                    originX, originY, originZ, radius)) {
                inspect(new Position(blockX, blockY, blockZ), snapshot);
            }
            if (++yIndex > maxY - minY) {
                yIndex = 0;
                if (++xIndex > maxX - minX) {
                    xIndex = 0;
                    if (++zIndex > maxZ - minZ) {
                        nextChunk();
                    }
                }
            }
        }
        if (complete()) {
            snapshot = null;
        }
    }

    // Start at the closest coordinate, then visit the lower and upper sides without allocating positions.
    private static int coordinate(int index, int min, int max, int origin) {
        int start = Math.clamp(origin, min, max);
        return index <= start - min ? start - index : min + index;
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

    private void nextChunk() {
        snapshot = null;
        xIndex = 0;
        yIndex = 0;
        zIndex = 0;
        do {
            // Enumerate only the perimeter of each chunk ring, nearest rings first.
            if (Math.abs(chunkOffsetZ) < ring && chunkOffsetX == -ring) {
                chunkOffsetX = ring;
            } else if (++chunkOffsetX > ring) {
                chunkOffsetX = -ring;
                if (++chunkOffsetZ > ring) {
                    if (++ring > maxRing) {
                        exhausted = true;
                        return;
                    }
                    chunkOffsetX = -ring;
                    chunkOffsetZ = -ring;
                }
            }
        } while ((originX >> 4) + chunkOffsetX < minChunkX
                || (originX >> 4) + chunkOffsetX > maxChunkX
                || (originZ >> 4) + chunkOffsetZ < minChunkZ
                || (originZ >> 4) + chunkOffsetZ > maxChunkZ);
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
