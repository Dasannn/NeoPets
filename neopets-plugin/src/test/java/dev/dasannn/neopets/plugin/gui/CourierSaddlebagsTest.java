package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.*;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.courier.CourierService;
import dev.dasannn.neopets.core.courier.Saddlebag;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.SaddlebagRepository;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.kyori.adventure.text.Component;

class CourierSaddlebagsTest {
    @TempDir Path directory;
    private final PlayerId owner = new PlayerId(UUID.randomUUID());
    private final PetId id = new PetId(UUID.randomUUID());
    private final OwnedPet pet = new OwnedPet(id, owner, new VariantId("donkey"), Optional.empty(),
            PetStatus.ALIVE, false, Instant.EPOCH);
    private final List<CompletableFuture<Saddlebag>> writes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<byte[]> payloads = new ArrayList<>();
    private final List<String> order = new ArrayList<>();
    private final ItemStack[] bag = new ItemStack[54];
    private ItemStack[] playerItems = new ItemStack[36];
    private InventoryHolder holder;
    private Inventory top;
    private Player player;
    private boolean online = true;
    private int opened;
    private CourierSaddlebags coordinator;
    private final SaddlebagContentsCodec codec = new SaddlebagContentsCodec(new SaddlebagContentsCodec.ItemSerialization() {
        public byte[] serialize(ItemStack item) { return new byte[] {(byte) item.getAmount()}; }
        public ItemStack deserialize(byte[] bytes) { return new Stack(Byte.toUnsignedInt(bytes[0])); }
    });

    @BeforeEach void setup() throws Exception {
        var config = new ConfigurationManager(new ConfigurationLoader(directory));
        var logger = Logger.getAnonymousLogger();
        var task = proxy(BukkitTask.class, (p, m, a) -> fallback(m.getReturnType()));
        var scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
            if (m.getName().equals("runTask")) ((Runnable) a[1]).run();
            return m.getReturnType() == BukkitTask.class ? task : fallback(m.getReturnType());
        });
        Inventory inventory = proxy(Inventory.class, (p, m, a) -> switch (m.getName()) {
            case "getHolder" -> holder;
            case "getSize" -> 54;
            case "getItem" -> bag[(int) a[0]];
            case "setItem" -> { bag[(int) a[0]] = (ItemStack) a[1]; yield null; }
            default -> fallback(m.getReturnType());
        });
        PlayerInventory playerInventory = proxy(PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
            case "getStorageContents" -> SaddlebagTransfer.copy(playerItems);
            case "setStorageContents" -> { playerItems = SaddlebagTransfer.copy((ItemStack[]) a[0]); yield null; }
            default -> fallback(m.getReturnType());
        });
        player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> owner.value();
            case "isOnline", "hasPermission" -> online;
            case "getInventory" -> playerInventory;
            case "getItemOnCursor" -> new Stack(0);
            case "getOpenInventory" -> view();
            case "openInventory" -> { top = (Inventory) a[0]; opened++; yield view(); }
            case "closeInventory" -> { top = null; yield null; }
            case "saveData" -> { order.add("player"); yield null; }
            default -> fallback(m.getReturnType());
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getScheduler" -> scheduler;
            case "isPrimaryThread" -> true;
            case "createInventory" -> { holder = (InventoryHolder) a[0]; yield inventory; }
            default -> fallback(m.getReturnType());
        });
        Plugin plugin = proxy(Plugin.class, (p, m, a) -> m.getName().equals("getServer") ? server : fallback(m.getReturnType()));
        var operations = new PlayerOperationQueue();
        var pets = proxy(PetRepository.class, (p, m, a) -> CompletableFuture.completedFuture(
                m.getName().equals("findById") ? Optional.of(pet) : m.getName().equals("findByOwner") ? List.of(pet) : null));
        var entities = proxy(PetEntityGateway.class, (p, m, a) -> CompletableFuture.completedFuture(null));
        var courier = new CourierService(pets, entities, operations, Clock.systemUTC(),
                () -> Duration.ofMinutes(5), () -> Duration.ofMinutes(15));
        SaddlebagRepository repository = new SaddlebagRepository() {
            public CompletableFuture<Saddlebag> loadOrCreate(PetId pet, PlayerId owner, byte[] empty) {
                return CompletableFuture.completedFuture(new Saddlebag(pet, owner, empty, 0));
            }
            public CompletableFuture<Saddlebag> save(PetId pet, PlayerId owner, byte[] contents, long version) {
                order.add("sql"); payloads.add(contents);
                var pending = new CompletableFuture<Saddlebag>(); writes.add(pending); return pending;
            }
        };
        coordinator = new CourierSaddlebags(plugin, new Menu(server, logger, Runnable::run, ignored -> null),
                config, new LocalizedMessages(config), repository, operations, courier, new RuntimeWorkTracker(),
                ignored -> {}, ignored -> Optional.of(pet), ignored -> "Rocinante", ignored -> true,
                logger, codec, (inv, layout) -> {});
    }

    @Test void secondViewDeniedUntilCloseSaveCompletes() {
        coordinator.openOwner(player, pet);
        var view = view(); top = null;
        coordinator.onClose(new InventoryCloseEvent(view));
        coordinator.openOwner(player, pet);
        assertEquals(1, opened);
        assertEquals(1, writes.size());
        finish(0);
        coordinator.openOwner(player, pet);
        assertEquals(2, opened);
    }

    @Test void quitSavesBeforeUnlocking() {
        coordinator.openOwner(player, pet);
        online = false;
        coordinator.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertEquals(1, writes.size());
        online = true;
        coordinator.openOwner(player, pet);
        assertEquals(1, opened);
        finish(0);
        coordinator.openOwner(player, pet);
        assertEquals(2, opened);
    }

    @Test void depositDurablyDebitsPlayerBeforeCreditingBag() throws Exception {
        coordinator.openOwner(player, pet);
        playerItems[0] = new Stack(12);
        transfer(false, 0);
        assertNull(playerItems[0]);
        assertNull(bag[10]);
        assertEquals(List.of("player", "sql"), order);
        finish(0);
        assertEquals(12, bag[10].getAmount());
    }

    @Test void withdrawalCreditsOnlyAfterSqlCommitAndPersistsPlayer() throws Exception {
        coordinator.openOwner(player, pet);
        bag[10] = new Stack(12);
        transfer(true, 0);
        assertNull(playerItems[0]);
        assertEquals(List.of("sql"), order);
        finish(0);
        assertEquals(List.of("sql", "player"), order);
        assertNull(bag[10]);
        assertEquals(12, playerItems[0].getAmount());
    }

    @Test void closedWithdrawalRestoresUnclaimedItemsBeforeRelease() throws Exception {
        coordinator.openOwner(player, pet);
        bag[10] = new Stack(12);
        transfer(true, 0);
        var view = view(); top = null;
        coordinator.onClose(new InventoryCloseEvent(view));
        finish(0);
        assertEquals(2, writes.size());
        assertNull(playerItems[0]);
        coordinator.openOwner(player, pet);
        assertEquals(1, opened);
        finish(1);
        assertEquals(12, codec.decode(payloads.get(1))[0].getAmount());
        coordinator.openOwner(player, pet);
        assertEquals(2, opened);
    }

    @Test void disableDrainsAnOpenViewSaveBeforeUnlocking() throws Exception {
        coordinator.openOwner(player, pet);
        var finisher = new Thread(() -> {
            while (writes.isEmpty()) Thread.onSpinWait();
            finish(0);
        });
        finisher.setDaemon(true);
        finisher.start();
        coordinator.close();
        finisher.join();
        coordinator.openOwner(player, pet);
        assertEquals(1, opened);
    }

    @Test void failedSaveRetainsExclusiveLease() {
        coordinator.openOwner(player, pet);
        var view = view(); top = null;
        coordinator.onClose(new InventoryCloseEvent(view));
        writes.getFirst().completeExceptionally(new IllegalStateException("storage unavailable"));
        coordinator.openOwner(player, pet);
        assertEquals(1, opened);
    }

    private void finish(int index) {
        writes.get(index).complete(new Saddlebag(id, owner, payloads.get(index), index + 1));
    }

    private void transfer(boolean withdraw, int slot) {
        var event = new org.bukkit.event.inventory.InventoryClickEvent(view(),
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                withdraw ? CourierMenuLayoutFactory.STORAGE_SLOTS.get(slot) : 54 + slot,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        coordinator.onClick(event);
        assertTrue(event.isCancelled(), "vanilla must never also execute a durable transfer");
    }

    private InventoryView view() {
        Inventory visible = top;
        return proxy(InventoryView.class, (p, m, a) -> switch (m.getName()) {
            case "getTopInventory" -> visible;
            case "getBottomInventory" -> player.getInventory();
            case "getInventory" -> (int) a[0] < 54 ? visible : player.getInventory();
            case "convertSlot" -> (int) a[0] < 54 ? (int) a[0] : (int) a[0] - 54;
            case "getPlayer" -> player;
            case "getType" -> org.bukkit.event.inventory.InventoryType.CHEST;
            case "getTitle", "getOriginalTitle" -> "Alforjas";
            default -> fallback(m.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
    private static Object fallback(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
    private static final class Stack extends ItemStack {
        private int amount;
        Stack(int amount) { this.amount = amount; }
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        public int getMaxStackSize() { return 64; }
        public boolean isEmpty() { return amount == 0; }
        public boolean isSimilar(ItemStack other) { return other instanceof Stack; }
        public Stack clone() { return new Stack(amount); }
    }
}
