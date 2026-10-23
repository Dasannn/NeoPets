package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.loot.Lootable;
import org.junit.jupiter.api.Test;

class TreasureScanTest {
    @Test
    void chunkTraversalCoversTheSphereOnceIncludingClippedWorldHeights() {
        for (int originY : new int[] {-64, 64, 319}) {
            var fixture = new Fixture();
            fixture.minHeight = -64;
            fixture.maxHeight = 320;
            fixture.defaultMaterial = Material.DIAMOND_ORE;
            var expected = new HashSet<TreasureScan.Position>();
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        int blockY = originY + y;
                        if (x * x + y * y + z * z <= 4 && blockY >= -64 && blockY < 320) {
                            expected.add(new TreasureScan.Position(x, blockY, z));
                        }
                    }
                }
            }
            var scan = fixture.scan(0, originY, 0, 2, 0, 1000, 1000);
            finish(scan);
            assertEquals(expected, Set.copyOf(scan.targets()));
            assertEquals(expected.size(), scan.targets().size(), "each block must be visited once");
        }
    }

    @Test
    void fullScanFindsAdjacentTreasureAtNormalWorldHeightAcrossChunkBoundaries() {
        var fixture = new Fixture();
        fixture.minHeight = -64;
        fixture.maxHeight = 320;
        var expected = Set.of(new TreasureScan.Position(1, 64, 0),
                new TreasureScan.Position(-1, 64, 0), new TreasureScan.Position(0, 64, -1),
                new TreasureScan.Position(0, 65, 0), new TreasureScan.Position(0, 63, 0));
        expected.forEach(position -> fixture.blocks.put(position, new Facts(Material.CHEST, true)));
        var scan = fixture.scan(0, 64, 0, 16, 0, 40_000, 32);

        finish(scan);

        assertEquals(expected, Set.copyOf(scan.targets()));
        assertTrue(scan.scannedBlocks() < 40_000);
    }

    @Test
    void chunkCenteredDefaultScanNeverRecapturesChunksAndFinishesPromptlyWithScent() {
        for (double scent : new double[] {0, 4, 20}) {
            var fixture = new Fixture();
            fixture.minHeight = -64;
            fixture.maxHeight = 320;
            var treasure = new TreasureScan.Position(8, 64, 8);
            fixture.blocks.put(treasure, new Facts(Material.DIAMOND_ORE, false));
            var scan = fixture.scan(8, 64, 8, 16, scent, 8192, 32);
            int calls = 0;
            while (!scan.complete()) {
                scan.advance(1024);
                assertTrue(++calls <= 10, "default scan must finish within five seconds of shared ticks");
            }
            assertEquals(Set.of(treasure), Set.copyOf(scan.targets()));
            assertEquals(8192, scan.scannedBlocks());
            assertEquals(fixture.capturedChunks.size(), fixture.snapshots,
                    "each chunk must be captured only once per activation");
        }
    }

    @Test
    void runtimeSelectsOnlyUnopenedLootAndConfiguredOreWithinScentRange() {
        var fixture = new Fixture();
        fixture.blocks.put(new TreasureScan.Position(1, 0, 0), new Facts(Material.CHEST, true));
        fixture.blocks.put(new TreasureScan.Position(2, 0, 0), new Facts(Material.CHEST, false));
        fixture.blocks.put(new TreasureScan.Position(3, 0, 0), new Facts(Material.BARREL, false));
        fixture.blocks.put(new TreasureScan.Position(4, 0, 0), new Facts(Material.IRON_ORE, false));
        fixture.blocks.put(new TreasureScan.Position(9, 0, 0), new Facts(Material.DIAMOND_ORE, false));
        fixture.blocks.put(new TreasureScan.Position(9, 0, 9), new Facts(Material.DIAMOND_ORE, false));

        var base = fixture.scan(0, 0, 0, 8, 0, 10_000, 20);
        finish(base);
        assertEquals(Set.of(new TreasureScan.Position(1, 0, 0)), Set.copyOf(base.targets()));

        var scented = fixture.scan(0, 0, 0, 8, 2, 10_000, 20);
        finish(scented);
        assertEquals(Set.of(new TreasureScan.Position(1, 0, 0), new TreasureScan.Position(9, 0, 0)),
                Set.copyOf(scented.targets()));
    }

    @Test
    void rejectedPositionsAndChunkDiscoveryConsumeBothBudgets() {
        var fixture = new Fixture();
        fixture.defaultMaterial = Material.DIAMOND_ORE;
        var capped = fixture.scan(8, 0, 8, 1, 0, 3, 20);
        finish(capped);
        // Snapshot acquisition and two inspected positions each consume budget.
        assertEquals(3, capped.scannedBlocks());
        assertEquals(2, capped.targets().size());

        var targetCapped = fixture.scan(8, 0, 8, 1, 0, 100, 2);
        finish(targetCapped);
        assertEquals(2, targetCapped.targets().size());
    }

    @Test
    void largeRadiusAboveBuildHeightFinishesWithoutDiscoveryOrTraversal() {
        var fixture = new Fixture();
        var scan = fixture.scan(0, 500, 0, 128, 0, 8192, 32);
        scan.advance(512);
        assertTrue(scan.complete());
        assertEquals(0, scan.scannedBlocks());
        assertEquals(0, fixture.chunkProbes);
        assertEquals(0, fixture.snapshots);
    }

    @Test
    void partialHeightOverlapClipsYAndResumesRejectedPositionsAcrossTicks() {
        var fixture = new Fixture();
        fixture.minHeight = -64;
        fixture.maxHeight = 320;
        var scan = fixture.scan(0, 440, 0, 128, 0, 1200, 32);
        scan.advance(512);
        assertFalse(scan.complete());
        assertTrue(scan.scannedBlocks() <= 512);
        assertEquals(1, fixture.snapshots);
        finish(scan);
        assertEquals(1200, scan.scannedBlocks());
        assertTrue(scan.targets().isEmpty());
    }

    @Test
    void denseChunksCreateNoEagerTileEntitySnapshotsAndRespectPerTickAndTotalCaps() {
        var fixture = new Fixture();
        fixture.maxHeight = 320;
        fixture.defaultMaterial = Material.CHEST;
        var scan = fixture.scan(8, 0, 8, 128, 0, 1300, 2000);
        assertEquals(0, fixture.snapshots);
        assertEquals(0, fixture.states);
        while (!scan.complete()) {
            int before = scan.scannedBlocks();
            int snapshotsBefore = fixture.snapshots;
            int statesBefore = fixture.states;
            scan.advance(512);
            assertTrue(scan.scannedBlocks() - before <= 512);
            assertTrue(fixture.snapshots - snapshotsBefore <= 1);
            assertTrue(fixture.states - statesBefore <= 512);
        }
        assertEquals(1300, scan.scannedBlocks());
        assertTrue(fixture.states <= 1300);

        // A small, fully intersecting dense chunk also verifies live state creation is budgeted.
        var dense = fixture.scan(8, 0, 8, 5, 0, 1000, 2000);
        int statesBefore = fixture.states;
        dense.advance(32);
        assertTrue(fixture.states - statesBefore > 0);
        assertTrue(fixture.states - statesBefore <= 31);
    }

    @Test
    void snapshotCreationIsSpreadAcrossTickerCallsEvenWhenChunksAreTiny() {
        var fixture = new Fixture();
        var scan = fixture.scan(0, 0, 0, 1, 0, 1000, 32);
        int ticks = 0;
        while (!scan.complete()) {
            int before = fixture.snapshots;
            scan.advance(512);
            assertTrue(fixture.snapshots - before <= 1);
            assertTrue(++ticks <= 5, "only the four intersecting chunks should be visited");
        }
        assertEquals(4, fixture.snapshots);
        assertEquals(13, scan.scannedBlocks()); // Four chunk probes plus nine cube positions.
    }

    @Test
    void unloadedNearbyChunksAreBudgetedAndNeverLoadedOrEnumeratedGlobally() {
        var fixture = new Fixture();
        fixture.loaded = ignored -> false;
        var scan = fixture.scan(0, 0, 0, 128, 0, 100, 32);
        scan.advance(7);
        assertEquals(7, fixture.chunkProbes);
        assertEquals(7, scan.scannedBlocks());
        assertFalse(scan.complete());
        finish(scan);
        assertEquals(100, scan.scannedBlocks());
        assertEquals(0, fixture.snapshots);
    }

    @Test
    void snapshotIsDiscardedIfItsChunkUnloadsBetweenTickerCalls() {
        var fixture = new Fixture();
        fixture.defaultMaterial = Material.DIAMOND_ORE;
        var scan = fixture.scan(8, 0, 8, 5, 0, 1000, 1000);
        scan.advance(32);
        int reads = fixture.blockReads;
        fixture.loaded = ignored -> false;
        finish(scan);
        assertEquals(reads, fixture.blockReads);
        assertEquals(1, fixture.snapshots);
    }

    private static void finish(TreasureScan scan) {
        for (int tick = 0; tick < 100 && !scan.complete(); tick++) {
            scan.advance(512);
        }
        assertTrue(scan.complete(), "scan must finish within its bounded activation budget");
    }

    private record Facts(Material material, boolean lootTable) {
    }

    private static final class Fixture {
        private final Map<TreasureScan.Position, Facts> blocks = new HashMap<>();
        private Material defaultMaterial = Material.AIR;
        private int minHeight;
        private int maxHeight = 1;
        private int snapshots;
        private final Set<TreasureScan.Position> capturedChunks = new HashSet<>();
        private int states;
        private int blockReads;
        private int chunkProbes;
        private Predicate<TreasureScan.Position> loaded = ignored -> true;
        private final World world = proxy(World.class, (proxy, method, args) -> switch (method.getName()) {
            case "getMinHeight" -> minHeight;
            case "getMaxHeight" -> maxHeight;
            case "isChunkLoaded" -> {
                chunkProbes++;
                yield loaded.test(new TreasureScan.Position((int) args[0], 0, (int) args[1]));
            }
            case "getChunkAt" -> chunk((int) args[0], (int) args[1]);
            case "getBlockAt" -> block((int) args[0], (int) args[1], (int) args[2]);
            default -> throw new AssertionError("Unexpected world call: " + method);
        });

        private TreasureScan scan(int x, int y, int z, double baseRadius, double scent,
                                  int maxScan, int maxTargets) {
            return new TreasureScan(world, x, y, z, TreasureTargetSelector.effectiveRadius(baseRadius, scent),
                    new BuscatesorosAbility.Settings(Duration.ofSeconds(30), baseRadius,
                            Duration.ofSeconds(5), maxTargets, maxScan, Set.of(Material.DIAMOND_ORE)));
        }

        private Chunk chunk(int chunkX, int chunkZ) {
            assertTrue(loaded.test(new TreasureScan.Position(chunkX, 0, chunkZ)), "must never load chunks");
            return proxy(Chunk.class, (proxy, method, args) -> {
                if (!method.getName().equals("getChunkSnapshot")) {
                    throw new AssertionError("No eager tile enumeration allowed: " + method);
                }
                snapshots++;
                capturedChunks.add(new TreasureScan.Position(chunkX, 0, chunkZ));
                return proxy(ChunkSnapshot.class, (snapshot, snapshotMethod, coordinates) -> {
                    if (!snapshotMethod.getName().equals("getBlockType")) {
                        throw new AssertionError("Unexpected snapshot call: " + snapshotMethod);
                    }
                    int y = (int) coordinates[1];
                    assertTrue(y >= minHeight && y < maxHeight, "Y must be clipped before reads");
                    blockReads++;
                    return facts((chunkX << 4) + (int) coordinates[0], y,
                            (chunkZ << 4) + (int) coordinates[2]).material();
                });
            });
        }

        private Block block(int x, int y, int z) {
            return proxy(Block.class, (proxy, method, args) -> {
                assertEquals("getState", method.getName());
                assertEquals(false, args[0], "do not create tile snapshots");
                states++;
                Class<?>[] interfaces = switch (facts(x, y, z).material()) {
                    case CHEST, BARREL -> new Class<?>[] {BlockState.class, Lootable.class};
                    default -> new Class<?>[] {BlockState.class};
                };
                return Proxy.newProxyInstance(getClass().getClassLoader(),
                        interfaces, (state, stateMethod, stateArgs) -> {
                            if (stateMethod.getName().equals("hasLootTable")) {
                                return facts(x, y, z).lootTable();
                            }
                            throw new AssertionError("Unexpected block state call: " + stateMethod);
                        });
            });
        }

        private Facts facts(int x, int y, int z) {
            return blocks.getOrDefault(new TreasureScan.Position(x, y, z), new Facts(defaultMaterial, false));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(TreasureScanTest.class.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
