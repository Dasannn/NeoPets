package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SoulItemProtectionPolicyTest {
    @ParameterizedTest(name = "{0} slot {1}")
    @MethodSource("blockedStations")
    void blocksSoulInsertionAtEveryProtectedStation(
            SoulItemProtectionPolicy.Station station, int inputSlot
    ) {
        int topSize = Math.max(9, inputSlot + 1);

        assertFalse(SoulItemProtectionPolicy.allows(station, inputSlot, topSize,
                SoulItemProtectionPolicy.Action.PLACE, SoulItemProtectionPolicy.SoulLocation.CURSOR));
        assertFalse(SoulItemProtectionPolicy.allows(station, inputSlot, topSize,
                SoulItemProtectionPolicy.Action.HOTBAR_SWAP, SoulItemProtectionPolicy.SoulLocation.HOTBAR));
        assertFalse(SoulItemProtectionPolicy.allows(station, topSize, topSize,
                SoulItemProtectionPolicy.Action.SHIFT_MOVE, SoulItemProtectionPolicy.SoulLocation.BOTTOM));
        assertFalse(SoulItemProtectionPolicy.allowsAutomatedInsertion(station));
        assertFalse(SoulItemProtectionPolicy.allowsDrag(station, topSize, Set.of(inputSlot, topSize)));
    }

    @Test
    void permitsRecoveryFromAStationAndMovementThroughNormalInventories() {
        assertTrue(SoulItemProtectionPolicy.allows(SoulItemProtectionPolicy.Station.ANVIL, 0, 3,
                SoulItemProtectionPolicy.Action.PICKUP, SoulItemProtectionPolicy.SoulLocation.TOP));
        assertTrue(SoulItemProtectionPolicy.allows(SoulItemProtectionPolicy.Station.NORMAL, 0, 27,
                SoulItemProtectionPolicy.Action.PLACE, SoulItemProtectionPolicy.SoulLocation.CURSOR));
        assertTrue(SoulItemProtectionPolicy.allowsAutomatedInsertion(SoulItemProtectionPolicy.Station.NORMAL));
        assertTrue(SoulItemProtectionPolicy.allowsDrag(SoulItemProtectionPolicy.Station.NORMAL, 27, Set.of(0, 27)));
    }

    @ParameterizedTest
    @MethodSource("blockedStations")
    void permitsMovingAndDroppingWithinPlayerInventoryWhileStationIsOpen(
            SoulItemProtectionPolicy.Station station, int inputSlot
    ) {
        int topSize = 10;
        assertTrue(SoulItemProtectionPolicy.allows(station, topSize, topSize,
                SoulItemProtectionPolicy.Action.PLACE, SoulItemProtectionPolicy.SoulLocation.CURSOR));
        assertTrue(SoulItemProtectionPolicy.allows(station, topSize, topSize,
                SoulItemProtectionPolicy.Action.HOTBAR_SWAP, SoulItemProtectionPolicy.SoulLocation.HOTBAR));
        assertTrue(SoulItemProtectionPolicy.allows(station, topSize, topSize,
                SoulItemProtectionPolicy.Action.OTHER, SoulItemProtectionPolicy.SoulLocation.BOTTOM));
        assertTrue(SoulItemProtectionPolicy.allows(station, -999, topSize,
                SoulItemProtectionPolicy.Action.OTHER, SoulItemProtectionPolicy.SoulLocation.CURSOR));
        assertTrue(SoulItemProtectionPolicy.allowsDrag(station, topSize, Set.of(topSize, topSize + 1)));
    }

    private static Stream<Arguments> blockedStations() {
        return Stream.of(SoulItemProtectionPolicy.Station.values())
                .filter(station -> station != SoulItemProtectionPolicy.Station.NORMAL)
                .flatMap(station -> {
                    int first = switch (station) {
                        case CRAFTING_TABLE, PLAYER_CRAFTING -> 1;
                        default -> 0;
                    };
                    int last = switch (station) {
                        case CRAFTING_TABLE -> 9;
                        case PLAYER_CRAFTING, BREWING -> 4;
                        case CRAFTER -> 8;
                        case ANVIL, GRINDSTONE, CARTOGRAPHY, ENCHANTING, FURNACE, SMOKER, BLAST_FURNACE -> 1;
                        case SMITHING, LOOM -> 2;
                        case CHISELED_BOOKSHELF -> 5;
                        default -> 0;
                    };
                    return IntStream.rangeClosed(first, last).mapToObj(slot -> Arguments.of(station, slot));
                });
    }
}
