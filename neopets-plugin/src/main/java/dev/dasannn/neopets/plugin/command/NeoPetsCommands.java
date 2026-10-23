package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.GiveResult;
import dev.dasannn.neopets.core.pet.ReviveResult;
import dev.dasannn.neopets.core.skilltree.SkillPointResult;
import dev.dasannn.neopets.plugin.NeoPetsPlugin;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.listener.SoulItemIdentity;
import dev.dasannn.neopets.plugin.runtime.NeoPetsRuntime;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Registers the Paper command API entry points and keeps Bukkit command glue thin. */
public final class NeoPetsCommands {
    private static final String USE = "neopets.use";
    private static final String GIVE = "neopets.admin.give";
    private static final String SP = "neopets.admin.sp";
    private static final String REVIVE = "neopets.admin.revive";
    private static final String RELOAD = "neopets.admin.reload";

    private final NeoPetsPlugin plugin;
    private final Supplier<NeoPetsRuntime> runtime;

    public NeoPetsCommands(NeoPetsPlugin plugin, Supplier<NeoPetsRuntime> runtime) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** Registers both command roots, including the {@code /pets} alias. */
    public void register(Commands commands) {
        commands.register("neopets", "Abre tus mascotas", List.of("pets"), new PlayerPetsCommand());
        commands.register("neopet", "Administra NeoPets", List.of(), new PetAdminCommand());
    }

    private final class PlayerPetsCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            var rt = runtime.get();
            CommandSender sender = source.getSender();
            if (args.length > 0 && args[0].equalsIgnoreCase("revive")) {
                revive(rt, sender, args);
                return;
            }
            if (!allowed(sender, USE, rt)) {
                return;
            }
            if (!(sender instanceof Player player)) {
                rt.messages().send(sender, MessageSpec.of("errors.player-only"));
                return;
            }
            if (args.length != 0) {
                rt.messages().send(sender, MessageSpec.of("general.usage-neopets"));
                return;
            }
            rt.petsMenu().open(player);
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length == 1 && source.getSender().hasPermission(REVIVE)) {
                return prefix(List.of("revive"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("revive")
                    && source.getSender().hasPermission(REVIVE)) {
                return onlinePlayerNames(args[1]);
            }
            return List.of();
        }
    }

    private final class PetAdminCommand implements BasicCommand {
        @Override
        public void execute(CommandSourceStack source, String[] args) {
            var rt = runtime.get();
            if (args.length == 0) {
                rt.messages().send(source.getSender(), MessageSpec.of("general.usage-neopet"));
                return;
            }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give" -> give(rt, source.getSender(), args);
                case "summon" -> summonOrDismiss(rt, source.getSender(), args, true);
                case "dismiss" -> summonOrDismiss(rt, source.getSender(), args, false);
                case "sp" -> skillPoints(rt, source.getSender(), args);
                case "reload" -> reload(rt, source.getSender(), args);
                default -> rt.messages().send(source.getSender(), MessageSpec.of("general.usage-neopet"));
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length == 1) {
                var choices = new ArrayList<String>();
                if (source.getSender().hasPermission(USE)) {
                    choices.addAll(List.of("summon", "dismiss"));
                }
                if (source.getSender().hasPermission(GIVE)) {
                    choices.add("give");
                }
                if (source.getSender().hasPermission(SP)) {
                    choices.add("sp");
                }
                if (source.getSender().hasPermission(RELOAD)) {
                    choices.add("reload");
                }
                return prefix(choices, args[0]);
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
                if (args.length == 2) {
                    return onlinePlayerNames(args[1]);
                }
                if (args.length == 3) {
                    var values = new ArrayList<String>();
                    values.add("random");
                    runtime.get().configuration().current().variants().findAll().stream()
                            .map(variant -> variant.id().value()).forEach(values::add);
                    return prefix(values, args[2]);
                }
                if (args.length == 4 && args[2].equalsIgnoreCase("random")) {
                    return prefix(runtime.get().configuration().current().variants().findAll().stream()
                            .map(variant -> variant.type().value()).distinct().toList(), args[3]);
                }
            }
            if (args.length == 2 && (args[0].equalsIgnoreCase("summon")
                    || args[0].equalsIgnoreCase("dismiss")) && source.getSender() instanceof Player player) {
                return ownedPetSuggestions(player, args[1]);
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("sp")) {
                if (args.length == 2) {
                    return prefix(List.of("give", "take", "set"), args[1]);
                }
                if (args.length == 3) {
                    return onlinePlayerNames(args[2]);
                }
            }
            return List.of();
        }
    }

    private void give(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, GIVE, rt)) {
            return;
        }
        if (args.length < 3 || args.length > 4) {
            rt.messages().send(sender, MessageSpec.of("general.usage-give"));
            return;
        }
        OfflinePlayer target = target(args[1]);
        if (target == null) {
            rt.messages().send(sender, new MessageSpec("errors.player-not-found", Map.of("player", args[1])));
            return;
        }
        GiveArgument selection;
        try {
            selection = GiveArgument.parse(Arrays.copyOfRange(args, 2, args.length));
        } catch (IllegalArgumentException exception) {
            rt.messages().send(sender, MessageSpec.of("general.usage-give"));
            return;
        }
        if (selection instanceof GiveArgument.Random random && random.typeId().isPresent()
                && rt.configuration().current().variants().findByType(random.typeId().orElseThrow()).isEmpty()) {
            rt.messages().send(sender, new MessageSpec("pets.unknown-type",
                    Map.of("type", random.typeId().orElseThrow().value())));
            return;
        }

        PlayerId playerId = new PlayerId(target.getUniqueId());
        CompletableFuture<GiveResult> result = rt.submit(() -> switch (selection) {
            case GiveArgument.Specific specific -> rt.petService().giveSpecific(playerId, specific.variantId());
            case GiveArgument.Candidates candidates -> rt.petService().give(playerId, candidates.variantIds());
            case GiveArgument.Random random -> rt.petService().giveRandom(playerId, random.typeId());
        });
        String targetName = Optional.ofNullable(target.getName()).orElse(args[1]);
        complete(result, sender, outcome -> {
            rt.messages().send(sender, PetResultMessages.give(outcome, targetName, rt::petName));
            if (outcome instanceof GiveResult.Granted granted && target.getPlayer() != null) {
                rt.messages().send(target.getPlayer(), new MessageSpec(
                        "pets.given", Map.of("pet", rt.petName(granted.pet()))));
            }
        });
    }

    private void summonOrDismiss(NeoPetsRuntime rt, CommandSender sender, String[] args, boolean summon) {
        if (!allowed(sender, USE, rt)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            rt.messages().send(sender, MessageSpec.of("errors.player-only"));
            return;
        }
        if (args.length != 2) {
            rt.messages().send(sender, MessageSpec.of("general.usage-pet-action"));
            return;
        }
        PlayerId playerId = new PlayerId(player.getUniqueId());
        Optional<OwnedPet> selected = findPet(rt.loadedPets(playerId), args[1]);
        if (selected.isEmpty()) {
            rt.messages().send(sender, MessageSpec.of("pets.not-owned"));
            return;
        }
        if (summon) {
            complete(rt.submit(() -> rt.petService().summon(playerId, selected.orElseThrow().id())), sender,
                    result -> rt.messages().send(sender, PetResultMessages.summon(result, rt::petName)));
        } else {
            complete(rt.submit(() -> rt.petService().dismiss(playerId, selected.orElseThrow().id())), sender,
                    result -> rt.messages().send(sender, PetResultMessages.dismiss(result, rt::petName)));
        }
    }

    private void skillPoints(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, SP, rt)) {
            return;
        }
        if (args.length != 4) {
            rt.messages().send(sender, MessageSpec.of("general.usage-sp"));
            return;
        }
        OfflinePlayer target = target(args[2]);
        if (target == null) {
            rt.messages().send(sender, new MessageSpec("errors.player-not-found", Map.of("player", args[2])));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3]);
            if (amount < 0 || (amount == 0 && !args[1].equalsIgnoreCase("set"))) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            rt.messages().send(sender, MessageSpec.of("errors.invalid-amount"));
            return;
        }
        CompletableFuture<SkillPointResult> result;
        try {
            PlayerId playerId = new PlayerId(target.getUniqueId());
            result = switch (args[1].toLowerCase(Locale.ROOT)) {
                case "give" -> rt.submit(() -> rt.skillPoints().give(playerId, amount));
                case "take" -> rt.submit(() -> rt.skillPoints().take(playerId, amount));
                case "set" -> rt.submit(() -> rt.skillPoints().set(playerId, amount));
                default -> null;
            };
        } catch (IllegalArgumentException exception) {
            result = null;
        }
        if (result == null) {
            rt.messages().send(sender, MessageSpec.of("general.usage-sp"));
            return;
        }
        String targetName = Optional.ofNullable(target.getName()).orElse(args[2]);
        complete(result, sender, outcome ->
                rt.messages().send(sender, SkillPointResultMessages.change(outcome, targetName)));
    }

    private void revive(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, REVIVE, rt)) {
            return;
        }
        if (args.length != 2) {
            rt.messages().send(sender, MessageSpec.of("general.usage-revive"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            sendReviveOutcome(rt, sender, new ReviveCommandResult.PlayerOffline(args[1]), args[1], "");
            return;
        }

        SoulItemIdentity.ReadResult read = SoulItemIdentity.read(target.getInventory().getItemInMainHand());
        if (read instanceof SoulItemIdentity.ReadResult.Missing) {
            sendReviveOutcome(rt, sender, new ReviveCommandResult.NoSoulItem(), target.getName(), "");
            return;
        }
        if (read instanceof SoulItemIdentity.ReadResult.Invalid) {
            sendReviveOutcome(rt, sender, new ReviveCommandResult.InvalidSoul(), target.getName(), "");
            return;
        }
        SoulItemIdentity identity = ((SoulItemIdentity.ReadResult.Found) read).identity();
        PlayerId playerId = new PlayerId(target.getUniqueId());
        if (!identity.ownerId().equals(playerId)) {
            sendReviveOutcome(rt, sender, new ReviveCommandResult.WrongOwner(), target.getName(), "");
            return;
        }

        ItemStack reserved = reserveOneMainHandItem(target);
        CompletableFuture<ReviveResult> result = rt.submit(() -> rt.petService().revive(
                playerId, identity.petId(), identity.tokenId(), identity.ownerId(), rt.soulItems()));
        result.whenComplete((coreResult, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure != null) {
                restoreReservedItem(target, reserved);
                plugin.getLogger().log(Level.WARNING, "NeoPets revive command failed", unwrap(failure));
                rt.messages().send(sender, MessageSpec.of("errors.internal"));
                return;
            }
            ReviveCommandResult outcome = ReviveCommandResult.fromCore(coreResult);
            if (!(outcome instanceof ReviveCommandResult.Revived revived)) {
                restoreReservedItem(target, reserved);
                sendReviveOutcome(rt, sender, outcome, target.getName(), "");
                return;
            }
            String petName = rt.petName(revived.pet());
            rt.messages().send(target, new MessageSpec("pets.revived", Map.of("pet", petName)));
            sendReviveOutcome(rt, sender, outcome, target.getName(), petName);
        }));
    }

    private void sendReviveOutcome(
            NeoPetsRuntime rt,
            CommandSender sender,
            ReviveCommandResult outcome,
            String playerName,
            String petName
    ) {
        rt.messages().send(sender, ReviveResultMessages.sender(outcome, playerName, petName));
    }

    private static ItemStack reserveOneMainHandItem(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack reserved = held.clone();
        reserved.setAmount(1);
        if (held.getAmount() == 1) {
            player.getInventory().setItemInMainHand(ItemStack.empty());
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
        if (held.isEmpty()) {
            player.getInventory().setItemInMainHand(reserved);
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reserved);
        leftovers.values().forEach(item -> player.getWorld().dropItem(player.getLocation(), item));
    }

    private void reload(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, RELOAD, rt)) {
            return;
        }
        if (args.length != 1) {
            rt.messages().send(sender, MessageSpec.of("general.usage-neopet"));
            return;
        }
        try {
            rt.configuration().reload();
            rt.messages().send(sender, MessageSpec.of("general.reloaded"));
        } catch (IOException | ConfigurationLoadException exception) {
            plugin.getLogger().log(Level.WARNING, "NeoPets configuration reload failed: " + exception.getMessage());
            rt.messages().send(sender, MessageSpec.of("errors.config-invalid"));
        }
    }

    private boolean allowed(CommandSender sender, String permission, NeoPetsRuntime rt) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        rt.messages().send(sender, MessageSpec.of("errors.no-permission"));
        return false;
    }

    private OfflinePlayer target(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer cached = plugin.getServer().getOfflinePlayerIfCached(name);
        if (cached != null && cached.hasPlayedBefore()) {
            return cached;
        }
        return Arrays.stream(plugin.getServer().getOfflinePlayers())
                .filter(OfflinePlayer::hasPlayedBefore)
                .filter(player -> player.getName() != null && player.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    private Collection<String> onlinePlayerNames(String prefix) {
        return prefix(plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList(), prefix);
    }

    private Collection<String> ownedPetSuggestions(Player player, String input) {
        List<OwnedPet> owned = runtime.get().loadedPets(new PlayerId(player.getUniqueId()));
        var values = new ArrayList<String>();
        owned.forEach(pet -> {
            values.add(pet.variant().value());
            values.add(pet.id().toString());
        });
        return prefix(values, input);
    }

    private <T> void complete(CompletableFuture<T> future, CommandSender sender, Consumer<T> success) {
        future.whenComplete((result, failure) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (failure == null) {
                success.accept(result);
            } else {
                plugin.getLogger().log(Level.WARNING, "NeoPets command failed", unwrap(failure));
                runtime.get().messages().send(sender, MessageSpec.of("errors.internal"));
            }
        }));
    }

    private static Optional<OwnedPet> findPet(List<OwnedPet> pets, String token) {
        try {
            PetId id = new PetId(UUID.fromString(token));
            return pets.stream().filter(pet -> pet.id().equals(id)).findFirst();
        } catch (IllegalArgumentException ignored) {
            String variant = token.toLowerCase(Locale.ROOT);
            return pets.stream().filter(pet -> pet.variant().value().equals(variant)).findFirst();
        }
    }

    private static List<String> prefix(Collection<String> choices, String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
