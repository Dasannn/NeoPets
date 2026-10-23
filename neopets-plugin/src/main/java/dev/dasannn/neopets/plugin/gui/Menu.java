package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Renders and protects all NeoPets inventories through one shared Bukkit listener. */
public final class Menu implements Listener {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Server server;
    private final Logger logger;
    private final Consumer<Runnable> nextTick;
    private final Function<MenuItem, ItemStack> itemRenderer;

    public Menu(Plugin plugin, Logger logger) {
        Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.nextTick = task -> server.getScheduler().runTask(plugin, task);
        this.itemRenderer = Menu::render;
    }

    Menu(
            Server server,
            Logger logger,
            Consumer<Runnable> nextTick,
            Function<MenuItem, ItemStack> itemRenderer
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.nextTick = Objects.requireNonNull(nextTick, "nextTick");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
    }

    /** Opens a new protected inventory and returns its live session. */
    public Session open(Player player, MenuLayout layout, ActionHandler actionHandler) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(actionHandler, "actionHandler");
        var holder = new MenuHolder(player, layout, actionHandler, nextTick, itemRenderer);
        Inventory inventory = server.createInventory(holder, layout.size(), component(layout.title()));
        holder.attach(inventory);
        render(inventory, layout, itemRenderer);
        player.openInventory(inventory);
        return holder;
    }

    /** Cancels every click while a NeoPets menu is the top inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        boolean protectedMenu = event.getView().getTopInventory().getHolder(false) instanceof MenuHolder;
        if (!MenuProtection.cancelsClick(protectedMenu, event.getClick())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)
                || event.getRawSlot() < 0 || event.getRawSlot() >= holder.inventory().getSize()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuItem item = holder.layout().items().get(event.getRawSlot());
        if (item == null || item.actionId().isEmpty()) {
            return;
        }
        try {
            holder.actionHandler().handle(holder, player, item.actionId().orElseThrow(), event.getClick());
        } catch (RuntimeException failure) {
            logger.log(Level.SEVERE, "NeoPets menu action failed: " + item.actionId().orElseThrow(), failure);
        }
    }

    /** Cancels every drag while a NeoPets menu is the top inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        boolean protectedMenu = event.getView().getTopInventory().getHolder(false) instanceof MenuHolder;
        if (MenuProtection.cancelsDrag(protectedMenu)) {
            event.setCancelled(true);
        }
    }

    private static void render(
            Inventory inventory, MenuLayout layout, Function<MenuItem, ItemStack> itemRenderer
    ) {
        inventory.clear();
        layout.items().forEach((slot, item) -> inventory.setItem(slot, itemRenderer.apply(item)));
    }

    private static ItemStack render(MenuItem item) {
        Material material = Material.matchMaterial(item.material());
        if (material == null || material.isAir()) {
            throw new IllegalArgumentException("Unknown menu material: " + item.material());
        }
        var stack = new ItemStack(material, item.amount());
        stack.editMeta(meta -> {
            meta.itemName(component(item.name()));
            meta.lore(item.lore().stream().map(Menu::component).toList());
            meta.setEnchantmentGlintOverride(item.glint());
        });
        return stack;
    }

    private static Component component(String miniMessage) {
        return MINI_MESSAGE.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    /** Mutable handle to one inventory that can be refreshed without reopening it. */
    public interface Session {
        /** Replaces the current contents in place; row count and title must remain unchanged. */
        void refresh(MenuLayout layout);

        /** Closes this inventory for its viewer if it is still open. */
        void close();
    }

    /** Receives an item's action id and the exact Bukkit click type. */
    @FunctionalInterface
    public interface ActionHandler {
        void handle(Session session, Player player, String actionId, ClickType clickType);
    }

    private static final class MenuHolder implements InventoryHolder, Session {
        private final Player player;
        private final ActionHandler actionHandler;
        private final Consumer<Runnable> nextTick;
        private final Function<MenuItem, ItemStack> itemRenderer;
        private MenuLayout layout;
        private Inventory inventory;

        private MenuHolder(
                Player player,
                MenuLayout layout,
                ActionHandler actionHandler,
                Consumer<Runnable> nextTick,
                Function<MenuItem, ItemStack> itemRenderer
        ) {
            this.player = player;
            this.layout = layout;
            this.actionHandler = actionHandler;
            this.nextTick = nextTick;
            this.itemRenderer = itemRenderer;
        }

        private void attach(Inventory inventory) {
            if (this.inventory != null) {
                throw new IllegalStateException("inventory is already attached");
            }
            this.inventory = Objects.requireNonNull(inventory, "inventory");
        }

        @Override
        public Inventory getInventory() {
            return inventory();
        }

        private Inventory inventory() {
            return Objects.requireNonNull(inventory, "inventory has not been attached");
        }

        private MenuLayout layout() {
            return layout;
        }

        private ActionHandler actionHandler() {
            return actionHandler;
        }

        @Override
        public void refresh(MenuLayout changedLayout) {
            Objects.requireNonNull(changedLayout, "layout");
            if (changedLayout.size() != inventory().getSize()) {
                throw new IllegalArgumentException("an in-place refresh cannot change the inventory size");
            }
            if (player.getOpenInventory().getTopInventory() != inventory()) {
                return;
            }
            layout = changedLayout;
            render(inventory(), changedLayout, itemRenderer);
        }

        @Override
        public void close() {
            nextTick.accept(() -> {
                if (player.getOpenInventory().getTopInventory() == inventory()) {
                    player.closeInventory();
                }
            });
        }
    }
}
