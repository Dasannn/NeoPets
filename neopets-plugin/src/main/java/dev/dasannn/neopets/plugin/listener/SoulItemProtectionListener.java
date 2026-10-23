package dev.dasannn.neopets.plugin.listener;

import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.block.Crafter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Prevents PDC-tagged soul items from being transformed, consumed, placed, or displayed. */
public final class SoulItemProtectionListener implements Listener {
    /** Blocks direct and shift-click insertion while still allowing removal from a station. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        var view = event.getView();
        int topSize = view.getTopInventory().getSize();
        var station = station(view.getTopInventory().getType());
        var action = action(event.getAction());
        int rawSlot = event.getRawSlot();

        if (SoulItemIdentity.isSoul(event.getCursor())
                && !SoulItemProtectionPolicy.allows(station, rawSlot, topSize, action,
                SoulItemProtectionPolicy.SoulLocation.CURSOR)) {
            event.setCancelled(true);
            return;
        }
        if (SoulItemIdentity.isSoul(event.getCurrentItem())) {
            var location = rawSlot >= 0 && rawSlot < topSize
                    ? SoulItemProtectionPolicy.SoulLocation.TOP
                    : SoulItemProtectionPolicy.SoulLocation.BOTTOM;
            if (!SoulItemProtectionPolicy.allows(station, rawSlot, topSize, action, location)) {
                event.setCancelled(true);
                return;
            }
        }
        int hotbar = event.getHotbarButton();
        if (hotbar >= 0 || event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack item = hotbar >= 0 ? event.getWhoClicked().getInventory().getItem(hotbar)
                    : event.getWhoClicked().getInventory().getItemInOffHand();
            if (SoulItemIdentity.isSoul(item)
                    && !SoulItemProtectionPolicy.allows(station, rawSlot, topSize, action,
                    SoulItemProtectionPolicy.SoulLocation.HOTBAR)) {
                event.setCancelled(true);
            }
        }
    }

    /** Blocks multi-slot dragging without scanning an inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (SoulItemIdentity.isSoul(event.getOldCursor())
                && !SoulItemProtectionPolicy.allowsDrag(
                station(event.getView().getTopInventory().getType()),
                event.getView().getTopInventory().getSize(), event.getRawSlots())) {
            event.setCancelled(true);
        }
    }

    /** Stops hoppers and other inventory automation from inserting souls into stations. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (SoulItemIdentity.isSoul(event.getItem())
                && !SoulItemProtectionPolicy.allowsAutomatedInsertion(
                station(event.getDestination().getType()))) {
            event.setCancelled(true);
        }
    }

    /** Clears ordinary crafting output when any bounded matrix slot carries a soul. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (containsSoul(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    /** Clears anvil, smithing, and grindstone results for soul inputs, including renames. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        if (containsSoulInputs(event.getInventory())) {
            event.setResult(null);
        }
    }

    /** Cancels autonomous crafter operation when one of its nine fixed slots contains a soul. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof Crafter crafter
                && containsSoul(crafter.getInventory().getContents())) {
            event.setCancelled(true);
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    /** Prevents a soul from being offered to an enchanting table. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (SoulItemIdentity.isSoul(event.getItem()) || soulAt(event.getInventory(), 1)) {
            event.setCancelled(true);
        }
    }

    /** Prevents enchanting even if another plugin bypassed preparation. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (SoulItemIdentity.isSoul(event.getItem()) || soulAt(event.getInventory(), 1)) {
            event.setCancelled(true);
        }
    }

    /** Prevents furnace-family input from cooking. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        if (SoulItemIdentity.isSoul(event.getSource())) {
            event.setCancelled(true);
        }
    }

    /** Prevents furnace-family fuel from burning. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        if (SoulItemIdentity.isSoul(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /** Prevents brewing ingredient or fuel use. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (containsSoul(event.getContents().getContents())) {
            event.setCancelled(true);
        }
    }

    /** Prevents brewing fuel being consumed before a brew starts. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrewingFuel(BrewingStandFuelEvent event) {
        if (SoulItemIdentity.isSoul(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    /** Denies item and block use for a soul in either hand. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (SoulItemIdentity.isSoul(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    /** Defensive placement guard for placeable configured soul materials. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (SoulItemIdentity.isSoul(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /** Prevents dispensers placing soul blocks or equipping soul armor onto armor stands. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getBlock().getType() == Material.DISPENSER && SoulItemIdentity.isSoul(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Defensive consumption guard independent of interaction behavior. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (SoulItemIdentity.isSoul(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Prevents using the soul itself to place an item frame. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (SoulItemIdentity.isSoul(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Prevents putting souls into item frames or armor stands. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if ((event.getRightClicked() instanceof ItemFrame || event.getRightClicked() instanceof ArmorStand)
                && SoulItemIdentity.isSoul(event.getPlayer().getInventory().getItem(event.getHand()))) {
            event.setCancelled(true);
        }
    }

    /** Uses the exact manipulated stack for armor stands when available. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (SoulItemIdentity.isSoul(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    private static boolean containsSoulInputs(Inventory inventory) {
        return switch (inventory.getType()) {
            case ANVIL, GRINDSTONE -> soulAt(inventory, 0) || soulAt(inventory, 1);
            case SMITHING -> soulAt(inventory, 0) || soulAt(inventory, 1) || soulAt(inventory, 2);
            default -> false;
        };
    }

    private static boolean soulAt(Inventory inventory, int slot) {
        return slot < inventory.getSize() && SoulItemIdentity.isSoul(inventory.getItem(slot));
    }

    private static boolean containsSoul(ItemStack[] items) {
        Objects.requireNonNull(items, "items");
        for (ItemStack item : items) {
            if (SoulItemIdentity.isSoul(item)) {
                return true;
            }
        }
        return false;
    }

    private static SoulItemProtectionPolicy.Action action(InventoryAction action) {
        return switch (action) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> SoulItemProtectionPolicy.Action.PLACE;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> SoulItemProtectionPolicy.Action.HOTBAR_SWAP;
            case MOVE_TO_OTHER_INVENTORY -> SoulItemProtectionPolicy.Action.SHIFT_MOVE;
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE -> SoulItemProtectionPolicy.Action.PICKUP;
            default -> SoulItemProtectionPolicy.Action.OTHER;
        };
    }

    private static SoulItemProtectionPolicy.Station station(org.bukkit.event.inventory.InventoryType type) {
        return switch (type) {
            case WORKBENCH -> SoulItemProtectionPolicy.Station.CRAFTING_TABLE;
            case CRAFTING -> SoulItemProtectionPolicy.Station.PLAYER_CRAFTING;
            case CRAFTER -> SoulItemProtectionPolicy.Station.CRAFTER;
            case ANVIL -> SoulItemProtectionPolicy.Station.ANVIL;
            case SMITHING -> SoulItemProtectionPolicy.Station.SMITHING;
            case GRINDSTONE -> SoulItemProtectionPolicy.Station.GRINDSTONE;
            case ENCHANTING -> SoulItemProtectionPolicy.Station.ENCHANTING;
            case STONECUTTER -> SoulItemProtectionPolicy.Station.STONECUTTER;
            case LOOM -> SoulItemProtectionPolicy.Station.LOOM;
            case CARTOGRAPHY -> SoulItemProtectionPolicy.Station.CARTOGRAPHY;
            case FURNACE -> SoulItemProtectionPolicy.Station.FURNACE;
            case SMOKER -> SoulItemProtectionPolicy.Station.SMOKER;
            case BLAST_FURNACE -> SoulItemProtectionPolicy.Station.BLAST_FURNACE;
            case BREWING -> SoulItemProtectionPolicy.Station.BREWING;
            case DECORATED_POT -> SoulItemProtectionPolicy.Station.DECORATED_POT;
            case CHISELED_BOOKSHELF -> SoulItemProtectionPolicy.Station.CHISELED_BOOKSHELF;
            case COMPOSTER -> SoulItemProtectionPolicy.Station.COMPOSTER;
            default -> SoulItemProtectionPolicy.Station.NORMAL;
        };
    }
}
