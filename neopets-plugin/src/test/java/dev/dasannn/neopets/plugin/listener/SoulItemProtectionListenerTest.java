package dev.dasannn.neopets.plugin.listener;

import static dev.dasannn.neopets.plugin.listener.SoulItemTestItems.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class SoulItemProtectionListenerTest {
    private final SoulItemProtectionListener listener = new SoulItemProtectionListener();
    private final ItemStack soul = SoulItemTestItems.item(Map.of(SoulItemKeys.TOKEN_ID, "soul"));

    @Test
    void deniesUseInEitherHandIncludingAirInteractionsAlreadyPredictedCancelled() {
        Player player = proxy(Player.class, (proxy, method, args) -> null);
        for (var hand : new EquipmentSlot[] {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            for (var action : new Action[] {Action.RIGHT_CLICK_AIR, Action.LEFT_CLICK_AIR}) {
                var event = new PlayerInteractEvent(player, action, soul, null, null, hand);
                event.setCancelled(true);

                listener.onInteract(event);

                assertEquals(Event.Result.DENY, event.useItemInHand());
                assertEquals(Event.Result.DENY, event.useInteractedBlock());
            }
        }
    }

    @Test
    void blocksBrewingFuelBeforeItIsConsumed() {
        Block block = proxy(Block.class, (proxy, method, args) -> null);
        var event = new BrewingStandFuelEvent(block, soul, 20);

        listener.onBrewingFuel(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void preventsDispenserUseButAllowsDropperDrops() {
        for (var material : new Material[] {Material.DISPENSER, Material.DROPPER}) {
            Block block = proxy(Block.class, (proxy, method, args) -> switch (method.getName()) {
                case "getType" -> material;
                default -> throw new UnsupportedOperationException(method.getName());
            });
            var event = new BlockDispenseEvent(block, soul, new Vector());

            listener.onDispense(event);

            assertEquals(material == Material.DISPENSER, event.isCancelled());
        }
    }

}
