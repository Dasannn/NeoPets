package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.SoulToken;
import dev.dasannn.neopets.core.port.PendingSoulDeliveryRepository;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.platform.paper.TaskScheduler;
import java.util.Objects;
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
    private final SoulItemFactory items;
    private final SoulItemDelivery<ItemStack> delivery;
    private final LocalizedMessages messages;
    private final RuntimeWorkTracker work;
    private final Logger logger;
    private final PendingSoulDeliveryQueue<ItemStack> queue;

    public SoulDeliveryCoordinator(
            Server server,
            TaskScheduler scheduler,
            PendingSoulDeliveryRepository pending,
            PetRepository pets,
            SoulItemService souls,
            PlayerOperationQueue operations,
            SoulItemFactory items,
            SoulItemDelivery<ItemStack> delivery,
            LocalizedMessages messages,
            RuntimeWorkTracker work,
            Logger logger
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.items = Objects.requireNonNull(items, "items");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.work = Objects.requireNonNull(work, "work");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.queue = new PendingSoulDeliveryQueue<>(
                Objects.requireNonNull(pending, "pending"),
                Objects.requireNonNull(pets, "pets"),
                Objects.requireNonNull(souls, "souls"),
                Objects.requireNonNull(operations, "operations"),
                this.items::create,
                this::deliverIfOnline);
    }

    /** Durably queues a token, then immediately attempts delivery when its owner is online. */
    public CompletableFuture<Void> enqueueAndDeliver(SoulToken token) {
        return queue.enqueueAndDeliver(token);
    }

    /** Delivers all persisted souls after a player has joined. */
    public CompletableFuture<Void> deliverPending(Player player) {
        Objects.requireNonNull(player, "player");
        var ownerId = new PlayerId(player.getUniqueId());
        return queue.deliverPending(ownerId);
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

    private CompletableFuture<Boolean> deliverIfOnline(
            PlayerId ownerId, ItemStack item, dev.dasannn.neopets.core.model.OwnedPet pet
    ) {
        var result = new java.util.concurrent.atomic.AtomicBoolean();
        return scheduler.runOnMain(() -> {
            Player player = server.getPlayer(ownerId.value());
            if (player == null || !player.isOnline()) {
                return;
            }
            SoulItemDelivery.Result destination = delivery.deliver(item, SoulItemDelivery.player(player));
            String message = destination == SoulItemDelivery.Result.INVENTORY
                    ? "pets.soul-delivered" : "pets.soul-dropped";
            messages.send(player, new MessageSpec(message, java.util.Map.of("pet", items.petName(pet))));
            result.set(true);
        }).thenApply(ignored -> result.get());
    }
}
