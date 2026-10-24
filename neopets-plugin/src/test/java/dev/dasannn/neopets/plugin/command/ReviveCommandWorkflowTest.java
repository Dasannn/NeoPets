package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.ReviveResult;
import dev.dasannn.neopets.plugin.listener.SoulItemKeys;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class ReviveCommandWorkflowTest {
    private final PlayerId owner = new PlayerId(UUID.randomUUID());
    private final PetId petId = new PetId(UUID.randomUUID());
    private final OwnedPet alive = new OwnedPet(petId, owner, new VariantId("dog_brown"), Optional.empty(),
            PetStatus.ALIVE, false, Instant.EPOCH);
    private final List<Runnable> callbacks = new ArrayList<>();
    private final List<Sent> sent = new ArrayList<>();
    private final List<Throwable> failures = new ArrayList<>();
    private final List<ItemStack> returned = new ArrayList<>();
    private final CompletableFuture<ReviveResult> result = new CompletableFuture<>();
    private ItemStack held = new Stack(3);
    private int requests;
    private final PlayerInventory inventory = proxy(PlayerInventory.class, (proxy, method, args) -> switch (method.getName()) {
        case "getItemInMainHand" -> held;
        case "setItemInMainHand" -> { held = (ItemStack) args[0]; yield null; }
        case "addItem" -> { returned.addAll(Arrays.asList((ItemStack[]) args[0])); yield new HashMap<Integer, ItemStack>(); }
        default -> throw new UnsupportedOperationException(method.getName());
    });
    private final Player target = proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
        case "isOnline" -> true;
        case "getInventory" -> inventory;
        case "getUniqueId" -> owner.value();
        case "getName" -> "Alex";
        default -> throw new UnsupportedOperationException(method.getName());
    });
    private final CommandSender console = proxy(ConsoleCommandSender.class,
            (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
    private final ReviveCommandWorkflow workflow = new ReviveCommandWorkflow((player, identity) -> {
        requests++;
        assertEquals(owner, player);
        assertEquals(petId, identity.petId());
        return result;
    }, callbacks::add, (recipient, message) -> sent.add(new Sent(recipient, message)),
            pet -> "Luna", failures::add);

    @Test
    void consoleReviveReservesExactlyOneAndMessagesBothRecipientsOnMainThread() {
        workflow.execute(console, target, "Alex");
        assertEquals(2, held.getAmount());
        assertEquals(1, requests);
        assertTrue(sent.isEmpty());
        result.complete(new ReviveResult.Revived(alive));
        assertTrue(sent.isEmpty());
        callbacks.removeFirst().run();
        assertEquals(2, held.getAmount());
        assertTrue(returned.isEmpty());
        assertEquals(2, sent.size());
        assertSame(target, sent.get(0).recipient());
        assertEquals(new MessageSpec("pets.revived", Map.of("pet", "Luna")), sent.get(0).message());
        assertSame(console, sent.get(1).recipient());
        assertEquals(new MessageSpec("pets.revived-admin", Map.of("pet", "Luna", "player", "Alex")),
                sent.get(1).message());
    }

    @Test
    void lastMainHandItemIsClearedAndRestoredOnRejection() {
        held = new Stack(1);
        workflow.execute(console, target, "Alex");
        assertNull(held);
        result.complete(new ReviveResult.InvalidSoul(petId));
        callbacks.removeFirst().run();
        assertEquals(1, held.getAmount());
        assertTrue(returned.isEmpty());
    }

    @Test
    void rejectionRestoresExactlyTheReservedItemAndMessagesTheSender() {
        workflow.execute(console, target, "Alex");
        result.complete(new ReviveResult.InvalidSoul(petId));
        callbacks.removeFirst().run();
        assertEquals(2, held.getAmount());
        assertEquals(1, returned.size());
        assertEquals(1, returned.getFirst().getAmount());
        assertEquals(held.getItemMeta(), returned.getFirst().getItemMeta());
        assertSame(console, sent.getFirst().recipient());
        assertEquals(ReviveResultMessages.sender(new ReviveCommandResult.InvalidSoul(), "Alex", ""),
                sent.getFirst().message());
    }

    @Test
    void persistenceFailureRestoresTheItemAndReportsInternalError() {
        workflow.execute(console, target, "Alex");
        var failure = new IllegalStateException("write failed");
        result.completeExceptionally(failure);
        callbacks.removeFirst().run();
        assertEquals(1, returned.getFirst().getAmount());
        assertEquals(List.of(failure), failures);
        assertEquals(MessageSpec.of("errors.internal"), sent.getFirst().message());
    }

    @Test
    void emptyHandAndOfflineTargetDoNotSubmitOrReserve() {
        held = null;
        workflow.execute(console, target, "Alex");
        assertEquals(MessageSpec.of("pets.soul-required"), sent.getFirst().message());
        workflow.execute(console, null, "Offline");
        assertEquals(0, requests);
        assertTrue(callbacks.isEmpty());
        assertEquals(ReviveResultMessages.sender(new ReviveCommandResult.PlayerOffline("Offline"), "Offline", ""),
                sent.getLast().message());
    }

    private record Sent(CommandSender recipient, MessageSpec message) { }

    private final class Stack extends ItemStack {
        private int amount;
        private final ItemMeta meta;

        Stack(int amount) {
            this.amount = amount;
            var values = Map.of(SoulItemKeys.TOKEN_ID, UUID.randomUUID().toString(),
                    SoulItemKeys.PET_ID, petId.toString(), SoulItemKeys.OWNER, owner.toString());
            var data = proxy(PersistentDataContainer.class, (proxy, method, args) -> switch (method.getName()) {
                case "has" -> values.containsKey(args[0]);
                case "get" -> values.get(args[0]);
                default -> throw new UnsupportedOperationException(method.getName());
            });
            meta = proxy(ItemMeta.class, (proxy, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> data;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
        Stack(int amount, ItemMeta meta) { this.amount = amount; this.meta = meta; }
        @Override public int getAmount() { return amount; }
        @Override public void setAmount(int value) { amount = value; }
        @Override public boolean isEmpty() { return amount == 0; }
        @Override public Material getType() { return Material.ECHO_SHARD; }
        @Override public boolean hasItemMeta() { return true; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public ItemStack clone() { return new Stack(amount, meta); }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
