package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.courier.CourierRequest;
import dev.dasannn.neopets.core.courier.CourierResult;
import dev.dasannn.neopets.core.courier.CourierService;
import dev.dasannn.neopets.core.courier.Saddlebag;
import dev.dasannn.neopets.core.courier.SaddlebagViewLock;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.SaddlebagRepository;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.platform.paper.PetEntityMetadata;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Owns custom saddlebag inventories, exclusive view leases, persistence, and courier player interactions. */
public final class CourierSaddlebags implements Listener, AutoCloseable {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final long CLOSE_TIMEOUT_SECONDS = 15;

    private final Plugin plugin;
    private final Server server;
    private final Menu menu;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final SaddlebagRepository repository;
    private final PlayerOperationQueue operations;
    private final CourierService courier;
    private final RuntimeWorkTracker work;
    private final Logger logger;
    private final SaddlebagContentsCodec codec;
    private final java.util.function.BiConsumer<Inventory, MenuLayout> layoutRenderer;
    private final SaddlebagViewLock locks = new SaddlebagViewLock();
    private final Map<PetId, Session> sessions = new HashMap<>();
    private final Consumer<Player> petsMenuOpener;
    private final java.util.function.Function<PetId, Optional<OwnedPet>> petLookup;
    private final java.util.function.Function<OwnedPet, String> petName;
    private final java.util.function.Predicate<PetId> abilityActive;
    private final BukkitTask expiryTask;
    private volatile boolean stopped;
    private final java.util.Set<PetId> returning = new java.util.HashSet<>();
    private final java.util.Set<PlayerId> departing = new java.util.HashSet<>();
    private final java.util.concurrent.ConcurrentLinkedQueue<Runnable> completions = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<UUID, Long> openings = new HashMap<>();

    public CourierSaddlebags(
            Plugin plugin,
            Menu menu,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            SaddlebagRepository repository,
            PlayerOperationQueue operations,
            CourierService courier,
            RuntimeWorkTracker work,
            Consumer<Player> petsMenuOpener,
            java.util.function.Function<PetId, Optional<OwnedPet>> petLookup,
            java.util.function.Function<OwnedPet, String> petName,
            java.util.function.Predicate<PetId> abilityActive,
            Logger logger
    ) {
        this(plugin, menu, configuration, messages, repository, operations, courier, work,
                petsMenuOpener, petLookup, petName, abilityActive, logger, new SaddlebagContentsCodec(),
                CourierSaddlebags::renderLayout);
    }

    CourierSaddlebags(Plugin plugin, Menu menu, ConfigurationManager configuration,
            LocalizedMessages messages, SaddlebagRepository repository, PlayerOperationQueue operations,
            CourierService courier, RuntimeWorkTracker work, Consumer<Player> petsMenuOpener,
            java.util.function.Function<PetId, Optional<OwnedPet>> petLookup,
            java.util.function.Function<OwnedPet, String> petName,
            java.util.function.Predicate<PetId> abilityActive, Logger logger, SaddlebagContentsCodec codec,
            java.util.function.BiConsumer<Inventory, MenuLayout> layoutRenderer) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.layoutRenderer = Objects.requireNonNull(layoutRenderer, "layoutRenderer");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        server = plugin.getServer();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.courier = Objects.requireNonNull(courier, "courier");
        this.work = Objects.requireNonNull(work, "work");
        this.petsMenuOpener = Objects.requireNonNull(petsMenuOpener, "petsMenuOpener");
        this.petLookup = Objects.requireNonNull(petLookup, "petLookup");
        this.petName = Objects.requireNonNull(petName, "petName");
        this.abilityActive = Objects.requireNonNull(abilityActive, "abilityActive");
        this.logger = Objects.requireNonNull(logger, "logger");
        expiryTask = server.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    /** Opens an eligible owner's storage, or reports a localized denial. */
    public void openOwner(Player player, OwnedPet pet) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(pet, "pet");
        if (!pet.owner().value().equals(player.getUniqueId()) || !abilityActive.test(pet.id())
                || pet.status() != PetStatus.ALIVE
                || courier.requestFor(pet.id()).isPresent()) {
            messages.send(player, MessageSpec.of("courier.unavailable"));
            return;
        }
        open(player, pet, false);
    }

    /** Processes the clickable accept action. */
    public void accept(Player recipient, UUID requestId) {
        complete(recipient, courier.accept(requestId, new PlayerId(recipient.getUniqueId())), result -> {
            if (result instanceof CourierResult.Accepted accepted) {
                notifyAccepted(accepted.request());
                openRecipientIfPossible(recipient, accepted.request());
            } else {
                sendResult(recipient, result);
            }
        });
    }

    /** Processes the clickable reject action. */
    public void reject(Player recipient, UUID requestId) {
        complete(recipient, courier.reject(requestId, new PlayerId(recipient.getUniqueId())), result -> {
            sendResult(recipient, result);
            if (result instanceof CourierResult.Returned returned) notifyOwner(returned);
        });
    }

    private void open(Player player, OwnedPet pet, boolean recipient) {
        PlayerId viewer = new PlayerId(player.getUniqueId());
        var lease = locks.tryAcquire(pet.id(), viewer);
        if (lease.isEmpty()) {
            messages.send(player, MessageSpec.of("courier.inventory-in-use"));
            return;
        }
        long opening = openings.merge(player.getUniqueId(), 1L, Long::sum);
        repository.loadOrCreate(pet.id(), pet.owner(), codec.empty()).whenComplete((bag, failure) -> onMain(() -> {
            if (failure != null || stopped || !player.isOnline()
                    || openings.getOrDefault(player.getUniqueId(), 0L) != opening
                    || !eligible(player, pet, recipient)) {
                lease.orElseThrow().abort();
                if (failure != null) fail(player, "load saddlebag", failure);
                return;
            }
            ItemStack[] contents;
            try {
                contents = codec.decode(bag.contents());
            } catch (RuntimeException invalid) {
                lease.orElseThrow().abort();
                fail(player, "decode saddlebag", invalid);
                return;
            }
            createInventory(player, pet, bag, contents, recipient, lease.orElseThrow());
        }));
    }

    private void createInventory(
            Player player, OwnedPet pet, Saddlebag bag, ItemStack[] contents,
            boolean recipient, SaddlebagViewLock.Lease lease
    ) {
        var factory = new CourierMenuLayoutFactory(configuration.current().language());
        MenuLayout layout = recipient ? factory.recipient(petName.apply(pet), emptyDisplayContents())
                : factory.owner(petName.apply(pet), emptyDisplayContents());
        var holder = new Holder();
        Inventory inventory = server.createInventory(holder, layout.size(), component(layout.title()));
        holder.inventory = inventory;
        layoutRenderer.accept(inventory, layout);
        for (int index = 0; index < contents.length; index++) {
            inventory.setItem(CourierMenuLayoutFactory.STORAGE_SLOTS.get(index), cloneOrNull(contents[index]));
        }
        var session = new Session(player, pet, bag.version(), recipient, lease, holder);
        holder.session = session;
        synchronized (sessions) { sessions.put(pet.id(), session); }
        player.openInventory(inventory);
        if (player.getOpenInventory().getTopInventory() != inventory) beginSave(session);
    }

    private boolean eligible(Player viewer, OwnedPet pet, boolean recipient) {
        // Another queued return (for example owner quit racing recipient quit) may have
        // completed the trip without this GUI receiving a Returned notification.
        if (courier.requestFor(pet.id()).isEmpty()) returning.remove(pet.id());
        if (stopped || returning.contains(pet.id()) || departing.contains(pet.owner()) || !viewer.hasPermission("neopets.use") || !abilityActive.test(pet.id())) return false;
        OwnedPet current = petLookup.apply(pet.id()).orElse(null);
        if (current == null) return false;
        if (!recipient) return current.status() == PetStatus.ALIVE
                && current.owner().value().equals(viewer.getUniqueId()) && courier.requestFor(pet.id()).isEmpty();
        return courier.requestFor(pet.id()).filter(CourierRequest::accepted)
                .filter(request -> request.recipient().value().equals(viewer.getUniqueId())).isPresent();
    }

    private void openPicker(Player owner, OwnedPet pet, int page) {
        List<CourierMenuLayoutFactory.PlayerEntry> players = server.getOnlinePlayers().stream()
                .filter(player -> !player.getUniqueId().equals(owner.getUniqueId()))
                .map(player -> new CourierMenuLayoutFactory.PlayerEntry(player.getUniqueId(), player.getName()))
                .sorted(java.util.Comparator.comparing(CourierMenuLayoutFactory.PlayerEntry::name,
                        String.CASE_INSENSITIVE_ORDER)).toList();
        var factory = new CourierMenuLayoutFactory(configuration.current().language());
        var built = factory.picker(players, page);
        menu.open(owner, built.layout(), (session, viewer, action, click) -> {
            switch (action) {
                case CourierMenuLayoutFactory.ACTION_BACK -> openOwner(viewer, pet);
                case CourierMenuLayoutFactory.ACTION_HELP -> messages.send(viewer,
                        MessageSpec.of("courier.picker-help-message"));
                case CourierMenuLayoutFactory.ACTION_PREVIOUS -> openPicker(viewer, pet, built.pageIndex() - 1);
                case CourierMenuLayoutFactory.ACTION_NEXT -> openPicker(viewer, pet, built.pageIndex() + 1);
                default -> {
                    if (action.startsWith(CourierMenuLayoutFactory.ACTION_PLAYER_PREFIX)) {
                        send(viewer, pet, action.substring(CourierMenuLayoutFactory.ACTION_PLAYER_PREFIX.length()));
                    }
                }
            }
        });
    }

    private void send(Player owner, OwnedPet pet, String encodedRecipient) {
        if (!eligible(owner, pet, false) || locks.isLocked(pet.id())) {
            messages.send(owner, MessageSpec.of("courier.unavailable"));
            return;
        }
        UUID recipientId;
        try {
            recipientId = UUID.fromString(encodedRecipient);
        } catch (IllegalArgumentException invalid) {
            messages.send(owner, MessageSpec.of("courier.recipient-offline"));
            return;
        }
        Player recipient = server.getPlayer(recipientId);
        if (recipient == null || !recipient.isOnline() || recipient.equals(owner)) {
            messages.send(owner, MessageSpec.of("courier.recipient-offline"));
            return;
        }
        owner.closeInventory();
        complete(owner, courier.request(pet.id(), pet.owner(), new PlayerId(recipientId)), result -> {
            if (result instanceof CourierResult.Requested requested) {
                messages.send(owner, new MessageSpec("courier.request-sent", Map.of("player", recipient.getName())));
                sendClickableRequest(owner, recipient, requested.request());
            } else {
                sendResult(owner, result);
            }
        });
    }

    private void sendClickableRequest(Player owner, Player recipient, CourierRequest request) {
        Component prompt = messages.render(new MessageSpec("courier.request-received",
                Map.of("owner", owner.getName())));
        Component accept = messages.render(MessageSpec.of("courier.accept-button"))
                .clickEvent(ClickEvent.runCommand("/neopet courier accept " + request.id()));
        Component reject = messages.render(MessageSpec.of("courier.reject-button"))
                .clickEvent(ClickEvent.runCommand("/neopet courier reject " + request.id()));
        recipient.sendMessage(Component.text().append(messages.render(MessageSpec.of("prefix")))
                .append(Component.space()).append(prompt).append(Component.space())
                .append(accept).append(Component.space()).append(reject).build());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (transferring(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Holder holder)
                || holder.session == null) return;
        // No vanilla gesture can mutate the custom inventory, including collect-to-cursor
        // initiated in the bottom inventory. Transfers are confirmed only after SQL commits.
        event.setCancelled(true);
        Session session = holder.session;
        if (session.closing.get() || session.pending != null || !eligible(session.viewer, session.pet, session.recipient)) return;
        int raw = event.getRawSlot();
        boolean top = raw >= 0 && raw < event.getView().getTopInventory().getSize();
        if (top && !CourierMenuLayoutFactory.STORAGE_SLOTS.contains(raw)) {
            server.getScheduler().runTask(plugin, () -> handleControl(session, raw));
            return;
        }
        if (!SaddlebagInventoryPolicy.transfers(event.getClick()) || raw < 0
                || !session.viewer.getItemOnCursor().isEmpty()) return;
        int slot = top ? CourierMenuLayoutFactory.STORAGE_SLOTS.indexOf(raw) : event.getSlot();
        if (slot < 0 || (!top && slot >= 36)) return;
        transfer(session, top, slot, event.getClick() == ClickType.RIGHT);
    }

    private void handleControl(Session session, int raw) {
        if (session.closing.get() || session.pending != null
                || session.viewer.getOpenInventory().getTopInventory() != session.holder.inventory) return;
        if (raw == 47) {
            if (session.recipient) returnDelivery(session);
            else saveAndThen(session, () -> openPicker(session.viewer, session.pet, 0));
        } else if (raw == 49) {
            if (session.recipient) session.viewer.closeInventory();
            else saveAndThen(session, () -> petsMenuOpener.accept(session.viewer));
        } else if (raw == 51) {
            messages.send(session.viewer, MessageSpec.of("courier.storage-help-message"));
        }
    }

    private void transfer(Session session, boolean withdraw, int slot, boolean half) {
        ItemStack[] original = snapshot(session.holder.inventory);
        ItemStack[] bag = SaddlebagTransfer.copy(original);
        ItemStack[] player = SaddlebagTransfer.copy(session.viewer.getInventory().getStorageContents());
        ItemStack outgoing = withdraw ? cloneOrNull(original[slot]) : null;
        int moved = withdraw ? SaddlebagTransfer.move(bag, slot, player, half)
                : SaddlebagTransfer.move(player, slot, bag, half);
        if (moved == 0) return;
        if (outgoing != null) outgoing.setAmount(moved);
        byte[] bytes = codec.encode(bag);
        // Deposit: persist the player's debit before crediting the bag. Withdrawal: persist
        // the bag's debit before crediting the player. Never refund an ambiguous SQL failure.
        if (!withdraw) {
            session.viewer.getInventory().setStorageContents(player);
            session.viewer.saveData();
        }
        var pending = new CompletableFuture<Saddlebag>();
        session.pending = pending;
        operations.submit(session.pet.owner(), () -> repository.save(
                session.pet.id(), session.pet.owner(), bytes, session.version))
                .whenComplete((saved, failure) -> onMain(() -> {
                    try {
                        if (failure != null) {
                            pending.completeExceptionally(unwrap(failure));
                            fail(session.viewer, "transfer saddlebag items", failure);
                            beginSave(session);
                            return;
                        }
                        session.version = saved.version();
                        putContents(session.holder.inventory, bag);
                        ItemStack[] credit = SaddlebagTransfer.copy(session.viewer.getInventory().getStorageContents());
                        ItemStack[] delivery = new ItemStack[] {outgoing};
                        boolean deliverable = !withdraw || SaddlebagTransfer.move(delivery, 0, credit, false) == moved;
                        if (withdraw && (!session.viewer.isOnline() || session.closing.get() || stopped || !deliverable)) {
                            // No item has been credited. Put the unclaimed withdrawal back before unlocking.
                            operations.submit(session.pet.owner(), () -> repository.save(session.pet.id(),
                                    session.pet.owner(), codec.encode(original), saved.version()))
                                    .whenComplete((restored, restoreFailure) -> onMain(() -> {
                                        if (restoreFailure != null) pending.completeExceptionally(unwrap(restoreFailure));
                                        else {
                                            session.version = restored.version();
                                            putContents(session.holder.inventory, original);
                                            pending.complete(restored);
                                        }
                                        session.pending = null;
                                    }));
                        } else {
                            if (withdraw) {
                                session.viewer.getInventory().setStorageContents(credit);
                                session.viewer.saveData();
                            }
                            pending.complete(saved);
                            session.pending = null;
                        }
                    } catch (RuntimeException creditFailure) {
                        pending.completeExceptionally(creditFailure);
                        beginSave(session);
                        fail(session.viewer, "confirm saddlebag transfer", creditFailure);
                    }
                }));
    }

    private static void putContents(Inventory inventory, ItemStack[] contents) {
        for (int index = 0; index < contents.length; index++) {
            inventory.setItem(CourierMenuLayoutFactory.STORAGE_SLOTS.get(index), cloneOrNull(contents[index]));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        if (transferring(event.getWhoClicked().getUniqueId())
                || event.getView().getTopInventory().getHolder(false) instanceof Holder) event.setCancelled(true);
    }

    /** Also blocks other routes to a managed donkey's vanilla inventory. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVanillaOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder(false) instanceof Donkey donkey
                && PetEntityMetadata.petId(donkey).isPresent()) event.setCancelled(true);
    }

    private boolean transferring(UUID player) {
        synchronized (sessions) {
            return sessions.values().stream().anyMatch(s -> s.viewer.getUniqueId().equals(player) && s.pending != null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (transferring(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (transferring(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && transferring(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Holder holder && holder.session != null) {
            beginSave(holder.session);
        }
    }

    /** Transit invulnerability includes creative-player damage, which bypasses vanilla invulnerability. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageEvent event) {
        PetEntityMetadata.petId(event.getEntity()).filter(id -> courier.requestFor(id).isPresent())
                .ifPresent(ignored -> event.setCancelled(true));
    }

    /** Cancels every vanilla donkey inventory and opens an accepted courier only for its recipient. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDonkeyInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Donkey)) return;
        Optional<PetId> petId = PetEntityMetadata.petId(event.getRightClicked());
        if (petId.isEmpty()) return;
        event.setCancelled(true);
        courier.requestFor(petId.orElseThrow()).filter(CourierRequest::accepted)
                .filter(request -> request.recipient().value().equals(event.getPlayer().getUniqueId()))
                .ifPresent(request -> petLookup.apply(request.petId())
                        .ifPresent(pet -> open(event.getPlayer(), pet, true)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        recipientUnavailable(event.getPlayer(), CourierResult.Reason.RECIPIENT_QUIT);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        recipientUnavailable(event.getPlayer(), CourierResult.Reason.RECIPIENT_WORLD_CHANGE);
    }

    private void recipientUnavailable(Player player, CourierResult.Reason reason) {
        openings.merge(player.getUniqueId(), 1L, Long::sum);
        courier.activeRequests().stream().filter(request -> request.recipient().value().equals(player.getUniqueId()))
                .forEach(request -> returning.add(request.petId()));
        CompletableFuture<?> save = closeViewer(player.getUniqueId());
        save.thenCompose(ignored ->
                courier.recipientUnavailable(new PlayerId(player.getUniqueId()), reason))
                .whenComplete((returned, failure) -> onMain(() -> {
                    if (failure != null) logger.log(Level.WARNING, "Could not return courier", unwrap(failure));
                    else returned.forEach(this::notifyOwner);
                }));
    }

    private void returnDelivery(Session session) {
        returning.add(session.pet.id());
        saveAndThen(session, () -> complete(session.viewer,
                courier.returnToOwner(session.pet.id(), new PlayerId(session.viewer.getUniqueId())), result -> {
                    sendResult(session.viewer, result);
                    if (result instanceof CourierResult.Returned returned) notifyOwner(returned);
                }));
    }

    /** Saves every view of this owner's pets before lifecycle eviction and courier return. */
    public CompletableFuture<Void> ownerUnavailable(PlayerId owner) {
        departing.add(owner);
        List<Session> owned;
        synchronized (sessions) {
            owned = sessions.values().stream().filter(s -> s.pet.owner().equals(owner)).toList();
        }
        return CompletableFuture.allOf(owned.stream().map(this::beginSave).toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> courier.ownerUnavailable(owner));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        departing.remove(new PlayerId(event.getPlayer().getUniqueId()));
    }

    private CompletableFuture<?> closeViewer(UUID viewer) {
        Session found;
        synchronized (sessions) {
            found = sessions.values().stream().filter(s -> s.viewer.getUniqueId().equals(viewer)).findFirst().orElse(null);
        }
        return found == null ? CompletableFuture.completedFuture(null) : beginSave(found);
    }

    private CompletableFuture<Saddlebag> beginSave(Session session) {
        if (!session.closing.compareAndSet(false, true)) return session.save;
        if (session.viewer.getOpenInventory().getTopInventory() == session.holder.inventory) {
            session.viewer.closeInventory();
        }
        byte[] serialized = codec.encode(snapshot(session.holder.inventory));
        CompletableFuture<Saddlebag> durable = session.pending != null ? session.pending
                : operations.submit(session.pet.owner(), () -> repository.save(session.pet.id(), session.pet.owner(),
                        serialized, session.version));
        session.lease.releaseAfter(session.save);
        durable.whenComplete((saved, failure) -> {
            if (failure == null) {
                synchronized (sessions) { sessions.remove(session.pet.id(), session); }
                session.save.complete(saved);
            } else session.save.completeExceptionally(unwrap(failure));
        });
        return session.save;
    }

    private void saveAndThen(Session session, Runnable continuation) {
        beginSave(session).whenComplete((saved, failure) -> onMain(() -> {
            if (failure != null) {
                fail(session.viewer, "save saddlebag", failure);
                return;
            }
            if (session.viewer.getOpenInventory().getTopInventory() == session.holder.inventory) {
                session.viewer.closeInventory();
            }
            continuation.run();
        }));
    }

    private void expire() {
        Instant now = Instant.now();
        List<CourierRequest> expired = courier.activeRequests().stream()
                .filter(request -> !now.isBefore(request.expiresAt())).toList();
        expired.forEach(request -> returning.add(request.petId()));
        var saves = expired.stream().map(request -> {
            Session session;
            synchronized (sessions) { session = sessions.get(request.petId()); }
            return session == null ? CompletableFuture.completedFuture(null) : beginSave(session);
        }).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(saves).thenCompose(ignored -> courier.expire())
                .whenComplete((returned, failure) -> onMain(() -> {
                    if (failure != null) logger.log(Level.WARNING, "Could not expire courier requests", unwrap(failure));
                    else returned.forEach(this::notifyOwner);
                }));
    }

    private void openRecipientIfPossible(Player recipient, CourierRequest request) {
        petLookup.apply(request.petId()).ifPresent(pet -> open(recipient, pet, true));
    }

    private void notifyAccepted(CourierRequest request) {
        Player owner = server.getPlayer(request.owner().value());
        Player recipient = server.getPlayer(request.recipient().value());
        if (owner != null && recipient != null) {
            messages.send(owner, new MessageSpec("courier.accepted", Map.of("player", recipient.getName())));
        }
        if (recipient != null) messages.send(recipient, MessageSpec.of("courier.accepted-recipient"));
    }

    private void notifyOwner(CourierResult.Returned returned) {
        returning.remove(returned.request().petId());
        Player owner = server.getPlayer(returned.request().owner().value());
        if (owner != null) messages.send(owner, MessageSpec.of(returned.reason() == CourierResult.Reason.TIMED_OUT
                ? "courier.timed-out" : "courier.returned"));
    }

    private void sendResult(Player player, CourierResult result) {
        MessageSpec message = switch (result) {
            case CourierResult.CoolingDown cooling -> new MessageSpec("courier.cooldown",
                    Map.of("time", Long.toString(Math.max(1, cooling.remaining().toSeconds())) + "s"));
            case CourierResult.Returned returned -> MessageSpec.of(returned.reason() == CourierResult.Reason.REJECTED
                    ? "courier.rejected" : "courier.returned");
            case CourierResult.WrongRecipient ignored -> MessageSpec.of("courier.unavailable");
            case CourierResult.UnknownRequest ignored -> MessageSpec.of("courier.unavailable");
            case CourierResult.AlreadyAccepted ignored -> MessageSpec.of("courier.unavailable");
            case CourierResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case CourierResult.Unavailable ignored -> MessageSpec.of("courier.unavailable");
            case CourierResult.Requested ignored -> MessageSpec.of("courier.request-sent");
            case CourierResult.Accepted ignored -> MessageSpec.of("courier.accepted-recipient");
        };
        messages.send(player, message);
    }

    private <T> void complete(Player player, CompletableFuture<T> future, Consumer<T> success) {
        work.submit(() -> future).whenComplete((result, failure) -> onMain(() -> {
            if (failure == null) success.accept(result);
            else fail(player, "complete courier operation", failure);
        }));
    }

    private ItemStack[] snapshot(Inventory inventory) {
        var contents = new ItemStack[SaddlebagContentsCodec.SLOT_COUNT];
        for (int index = 0; index < contents.length; index++) {
            contents[index] = cloneOrNull(inventory.getItem(CourierMenuLayoutFactory.STORAGE_SLOTS.get(index)));
        }
        return contents;
    }

    private static ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private static List<MenuItem> emptyDisplayContents() {
        return java.util.Collections.nCopies(SaddlebagContentsCodec.SLOT_COUNT, null);
    }

    private static Component component(String text) {
        return MINI_MESSAGE.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private static void renderLayout(Inventory inventory, MenuLayout layout) {
        layout.items().forEach((slot, item) -> {
            Material material = Material.matchMaterial(item.material());
            if (material == null || material.isAir()) return;
            var stack = new ItemStack(material, item.amount());
            stack.editMeta(meta -> {
                meta.itemName(component(item.name()));
                meta.lore(item.lore().stream().map(CourierSaddlebags::component).toList());
                meta.setEnchantmentGlintOverride(item.glint());
            });
            inventory.setItem(slot, stack);
        });
    }

    private void fail(Player player, String operation, Throwable failure) {
        logger.log(Level.WARNING, "Could not " + operation + " for " + player.getUniqueId(), unwrap(failure));
        if (player.isOnline()) messages.send(player, MessageSpec.of("errors.internal"));
    }

    private void onMain(Runnable action) {
        if (server.isPrimaryThread()) action.run();
        else {
            var claimed = new AtomicBoolean();
            Runnable once = () -> { if (claimed.compareAndSet(false, true)) action.run(); };
            completions.add(once);
            if (!stopped) server.getScheduler().runTask(plugin, () -> {
                completions.remove(once);
                once.run();
            });
        }
    }

    @Override
    public void close() throws Exception {
        stopped = true;
        expiryTask.cancel();
        List<Session> open;
        synchronized (sessions) { open = List.copyOf(sessions.values()); }
        CompletableFuture<?>[] saves = open.stream().map(this::beginSave).toArray(CompletableFuture[]::new);
        CompletableFuture<Void> all = CompletableFuture.allOf(saves);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CLOSE_TIMEOUT_SECONDS);
        // During disable the server cannot run scheduled callbacks while this method owns
        // its thread. Drain only our own completions; database work stays on its executor.
        while (!all.isDone() && System.nanoTime() < deadline) {
            Runnable completion;
            while ((completion = completions.poll()) != null) completion.run();
            try { all.get(10, TimeUnit.MILLISECONDS); }
            catch (java.util.concurrent.TimeoutException pending) { /* Await the next completion. */ }
        }
        all.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private Session session;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class Session {
        private final Player viewer;
        private final OwnedPet pet;
        private long version;
        private CompletableFuture<Saddlebag> pending;
        private final boolean recipient;
        private final SaddlebagViewLock.Lease lease;
        private final Holder holder;
        private final AtomicBoolean closing = new AtomicBoolean();
        private final CompletableFuture<Saddlebag> save = new CompletableFuture<>();

        private Session(Player viewer, OwnedPet pet, long version, boolean recipient,
                SaddlebagViewLock.Lease lease, Holder holder) {
            this.viewer = viewer;
            this.pet = pet;
            this.version = version;
            this.recipient = recipient;
            this.lease = lease;
            this.holder = holder;
        }
    }
}
