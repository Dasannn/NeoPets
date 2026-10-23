package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Set;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class SaddlebagInventoryPolicyTest {
    @Test
    void permitsOnlyExplicitOneWayTransfersAndBlocksEveryOtherGesture() {
        var allowed = Set.of(ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);
        for (ClickType click : ClickType.values()) {
            assertEquals(allowed.contains(click), SaddlebagInventoryPolicy.transfers(click), click.name());
        }
    }
}
