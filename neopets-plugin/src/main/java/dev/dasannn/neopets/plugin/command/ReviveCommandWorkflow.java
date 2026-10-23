package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.ReviveResult;
import dev.dasannn.neopets.plugin.listener.SoulItemIdentity;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Main-thread item reservation and response handling for the revive command. */
final class ReviveCommandWorkflow {
    private final BiFunction<PlayerId, SoulItemIdentity, CompletableFuture<ReviveResult>> revive;
    private final Consumer<Runnable> mainThread;
    private final BiConsumer<CommandSender, MessageSpec> messages;
    private final Function<OwnedPet, String> petName;
    private final Consumer<Throwable> failures;

    ReviveCommandWorkflow(BiFunction<PlayerId, SoulItemIdentity, CompletableFuture<ReviveResult>> revive,
            Consumer<Runnable> mainThread, BiConsumer<CommandSender, MessageSpec> messages,
            Function<OwnedPet, String> petName, Consumer<Throwable> failures) {
        this.revive = revive;
        this.mainThread = mainThread;
        this.messages = messages;
        this.petName = petName;
        this.failures = failures;
    }

    void execute(CommandSender sender, Player target, String requestedName) {
        if (target == null || !target.isOnline()) {
            sendOutcome(sender, new ReviveCommandResult.PlayerOffline(requestedName), requestedName, "");
            return;
        }

        SoulItemIdentity.ReadResult read = SoulItemIdentity.read(target.getInventory().getItemInMainHand());
        if (read instanceof SoulItemIdentity.ReadResult.Missing) {
            sendOutcome(sender, new ReviveCommandResult.NoSoulItem(), target.getName(), "");
            return;
        }
        if (read instanceof SoulItemIdentity.ReadResult.Invalid) {
            sendOutcome(sender, new ReviveCommandResult.InvalidSoul(), target.getName(), "");
            return;
        }
        SoulItemIdentity identity = ((SoulItemIdentity.ReadResult.Found) read).identity();
        PlayerId playerId = new PlayerId(target.getUniqueId());
        if (!identity.ownerId().equals(playerId)) {
            sendOutcome(sender, new ReviveCommandResult.WrongOwner(), target.getName(), "");
            return;
        }

        ItemStack reserved = reserveOneMainHandItem(target);
        CompletableFuture<ReviveResult> result = revive.apply(playerId, identity);
        result.whenComplete((coreResult, failure) -> mainThread.accept(() -> {
            if (failure != null) {
                restoreReservedItem(target, reserved);
                failures.accept(failure);
                messages.accept(sender, MessageSpec.of("errors.internal"));
                return;
            }
            ReviveCommandResult outcome = ReviveCommandResult.fromCore(coreResult);
            if (!(outcome instanceof ReviveCommandResult.Revived revived)) {
                restoreReservedItem(target, reserved);
                sendOutcome(sender, outcome, target.getName(), "");
                return;
            }
            String petName = this.petName.apply(revived.pet());
            messages.accept(target, new MessageSpec("pets.revived", Map.of("pet", petName)));
            sendOutcome(sender, outcome, target.getName(), petName);
        }));
    }

    private static ItemStack reserveOneMainHandItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack reserved = held.clone();
        reserved.setAmount(1);
        if (held.getAmount() == 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
        }
        return reserved;
    }

    private static void restoreReservedItem(Player player, ItemStack reserved) {
        if (!player.isOnline()) {
            // A disconnected player's inventory may already have been saved.
            player.getWorld().dropItem(player.getLocation(), reserved);
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.isEmpty()) {
            player.getInventory().setItemInMainHand(reserved);
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reserved);
        leftovers.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
    }

    private void sendOutcome(CommandSender sender, ReviveCommandResult outcome,
            String playerName, String name) {
        messages.accept(sender, ReviveResultMessages.sender(outcome, playerName, name));
    }
}
