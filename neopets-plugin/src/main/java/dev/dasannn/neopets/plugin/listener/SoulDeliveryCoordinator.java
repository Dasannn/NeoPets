package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PendingSoulDeliveryRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.platform.paper.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

/** Persists issued souls before delivering them now or on the owner's next join. */
public final class SoulDeliveryCoordinator implements Listener {
    private final Server server;
    private final TaskScheduler scheduler;
    private final PendingSoulDeliveryRepository pending;
    private final PetRepository pets;
    private final SoulItemFactory items;
    private final SoulItemDelivery<ItemStack> delivery;
    private final LocalizedMessages messages;
    private final RuntimeWorkTracker work;
    private final Logger logger;

    public SoulDeliveryCoordinator(
            Server server,
            TaskScheduler scheduler,
            PendingSoulDeliveryRepository pending,
            PetRepository pets,
            SoulItemFactory items,
            SoulItemDelivery<ItemStack> delivery,
            LocalizedMessages messages,
            RuntimeWorkTracker work,
            Logger logger
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.items = Objects.requireNonNull(items, "items");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.work = Objects.requireNonNull(work, "work");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Durably queues a token, then immediately attempts delivery when its owner is online. */
    public CompletableFuture<Void> enqueueAndDeliver(SoulToken token) {
        PendingSoulDelivery queued = PendingSoulDelivery.from(token);
        return pending.save(queued).thenCompose(ignored -> load(queued)).thenCompose(loaded -> {
            var delivered = new ArrayList<UUID>(1);
            return scheduler.runOnMain(() -> {
                Player player = server.getPlayer(queued.ownerId().value());
                if (player != null && player.isOnline()) {
                    deliver(player, loaded, delivered);
                }
            }).thenCompose(ignored -> deleteAll(delivered));
        });
    }

    /** Delivers all persisted souls after a player has joined. */
    public CompletableFuture<Void> deliverPending(Player player) {
        Objects.requireNonNull(player, "player");
        var ownerId = new PlayerId(player.getUniqueId());
        return pending.findByOwner(ownerId).thenCompose(queued -> loadAll(queued).thenCompose(loaded -> {
            var delivered = new ArrayList<UUID>(loaded.size());
            return scheduler.runOnMain(() -> {
                if (!player.isOnline()) {
                    return;
                }
                for (LoadedDelivery soul : loaded) {
                    deliver(player, soul, delivered);
                }
            }).thenCompose(ignored -> deleteAll(delivered));
        }));
    }

    /** Starts pending delivery after the ordinary player-data join work has been admitted. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        work.submit(() -> deliverPending(player)).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Could not deliver pending souls for " + player.getUniqueId(), failure);
            }
        });
    }

    private CompletableFuture<LoadedDelivery> load(PendingSoulDelivery queued) {
        return pets.findById(queued.petId()).thenApply(found -> new LoadedDelivery(queued,
                found.orElseThrow(() -> new IllegalStateException(
                        "Missing pet for pending soul delivery " + queued.tokenId()))));
    }

    private CompletableFuture<List<LoadedDelivery>> loadAll(List<PendingSoulDelivery> queued) {
        var futures = queued.stream().map(this::load).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    private void deliver(Player player, LoadedDelivery loaded, List<UUID> delivered) {
        PendingSoulDelivery queued = loaded.queued();
        if (!player.getUniqueId().equals(queued.ownerId().value())) {
            throw new IllegalArgumentException("player does not own pending soul delivery");
        }
        SoulItemDelivery.Result result = delivery.deliver(
                items.create(queued, loaded.pet()), SoulItemDelivery.player(player));
        String petName = items.petName(loaded.pet());
        String message = result == SoulItemDelivery.Result.INVENTORY
                ? "pets.soul-delivered" : "pets.soul-dropped";
        messages.send(player, new MessageSpec(message, java.util.Map.of("pet", petName)));
        delivered.add(queued.tokenId());
    }

    private CompletableFuture<Void> deleteAll(List<UUID> tokenIds) {
        return CompletableFuture.allOf(tokenIds.stream()
                .map(pending::delete).toArray(CompletableFuture[]::new));
    }

    private record LoadedDelivery(PendingSoulDelivery queued, OwnedPet pet) {
    }
}
