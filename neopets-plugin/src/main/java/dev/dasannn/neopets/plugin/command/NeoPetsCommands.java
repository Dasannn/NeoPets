package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.GiveResult;
import dev.dasannn.neopets.core.skilltree.SkillPointResult;
import dev.dasannn.neopets.plugin.NeoPetsPlugin;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.runtime.NeoPetsRuntime;
import dev.dasannn.neopets.plugin.update.SelfUpdater;
import dev.dasannn.neopets.plugin.update.UpdateResult;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
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

/** Registers the Paper command API entry points and keeps Bukkit command glue thin. */
public final class NeoPetsCommands {
    private static final String USE = "neopets.use";
    private static final String GIVE = "neopets.admin.give";
    private static final String RESET = "neopets.admin.reset";
    private static final String SP = "neopets.admin.sp";
    private static final String ADMIN = "neopets.admin.gui";
    private static final String REVIVE = "neopets.admin.revive";
    private static final String UPDATE = "neopets.admin.update";
    private static final String RELOAD = "neopets.admin.reload";

    private final SelfUpdater updater = new SelfUpdater();
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
            if (args.length > 0 && args[0].equalsIgnoreCase("update")) {
                update(rt, sender, args);
                return;
            }
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
            if (args.length == 1) {
                var choices = new ArrayList<String>();
                if (CommandPermissions.has(source.getSender(), REVIVE)) choices.add("revive");
                if (CommandPermissions.has(source.getSender(), UPDATE)) choices.add("update");
                return prefix(choices, args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("update")
                    && CommandPermissions.has(source.getSender(), UPDATE)) return prefix(List.of("check"), args[1]);
            if (args.length == 2 && args[0].equalsIgnoreCase("revive")
                    && CommandPermissions.has(source.getSender(), REVIVE)) return onlinePlayerNames(args[1]);
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
                case "reset" -> reset(rt, source.getSender(), args);
                case "sp" -> skillPoints(rt, source.getSender(), args);
                case "courier" -> courier(rt, source.getSender(), args);
                case "admin" -> admin(rt, source.getSender(), args);
                case "reload" -> reload(rt, source.getSender(), args);
                default -> rt.messages().send(source.getSender(), MessageSpec.of("general.usage-neopet"));
            }
        }

        @Override
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
            if (args.length == 1) {
                var choices = new ArrayList<String>();
                if (CommandPermissions.has(source.getSender(), USE)) {
                    choices.addAll(List.of("summon", "dismiss", "courier"));
                }
                if (CommandPermissions.has(source.getSender(), GIVE)) {
                    choices.add("give");
                }
                if (CommandPermissions.has(source.getSender(), RESET)) choices.add("reset");
                if (CommandPermissions.has(source.getSender(), SP)) {
                    choices.add("sp");
                }
                if (CommandPermissions.has(source.getSender(), ADMIN)) {
                    choices.add("admin");
                }
                if (CommandPermissions.has(source.getSender(), RELOAD)) {
                    choices.add("reload");
                }
                return prefix(choices, args[0]);
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("reset")
                    && CommandPermissions.has(source.getSender(), RESET)) {
                if (args.length == 2) return onlinePlayerNames(args[1]);
                Player target = plugin.getServer().getPlayerExact(args[1]);
                if (args.length >= 3 && target != null) {
                    return AdminResetWorkflow.suggestions(runtime.get().loadedPets(
                            new PlayerId(target.getUniqueId())), List.of(Arrays.copyOfRange(args, 2, args.length)));
                }
                return List.of();
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("give")
                    && CommandPermissions.has(source.getSender(), GIVE)) {
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
            if (args.length >= 2 && args[0].equalsIgnoreCase("sp")
                    && CommandPermissions.has(source.getSender(), SP)) {
                if (args.length == 2) {
                    return prefix(List.of("give", "take", "set"), args[1]);
                }
                if (args.length == 3) {
                    return onlinePlayerNames(args[2]);
                }
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("courier")) {
                return prefix(List.of("accept", "reject"), args[1]);
            }
            return List.of();
        }
    }

    private void courier(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, USE, rt)) return;
        if (!(sender instanceof Player player)) {
            rt.messages().send(sender, MessageSpec.of("errors.player-only"));
            return;
        }
        if (args.length != 3) {
            rt.messages().send(sender, MessageSpec.of("general.usage-courier"));
            return;
        }
        UUID requestId;
        try {
            requestId = UUID.fromString(args[2]);
        } catch (IllegalArgumentException invalid) {
            rt.messages().send(sender, MessageSpec.of("courier.unavailable"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "accept" -> rt.courierSaddlebags().accept(player, requestId);
            case "reject" -> rt.courierSaddlebags().reject(player, requestId);
            default -> rt.messages().send(sender, MessageSpec.of("general.usage-courier"));
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
            GiveCompensation.run(outcome, rt.configuration().current().settings().giveAllOwnedCommand(),
                    targetName, target.getUniqueId(), command -> plugin.getServer().dispatchCommand(
                            plugin.getServer().getConsoleSender(), command));
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

    private void reset(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, RESET, rt)) return;
        if (args.length < 3) {
            rt.messages().send(sender, MessageSpec.of("general.usage-reset"));
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        PlayerId playerId = target == null ? null : new PlayerId(target.getUniqueId());
        String token = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        CompletableFuture<List<OwnedPet>> candidates;
        try {
            PetId requested = new PetId(UUID.fromString(token));
            candidates = playerId == null ? CompletableFuture.completedFuture(List.of())
                    : rt.submit(() -> rt.pets().findById(requested)).thenApply(found -> found.stream().toList());
        } catch (IllegalArgumentException ignored) {
            candidates = CompletableFuture.completedFuture(playerId == null ? List.of() : rt.loadedPets(playerId));
        }
        complete(candidates.thenCompose(pets -> AdminResetWorkflow.execute(playerId, pets,
                token, (owner, pet) -> rt.submit(() -> rt.skillTrees().reset(owner, pet, true)))), sender,
                result -> rt.messages().send(sender, AdminResetWorkflow.message(result, args[1], token, rt::petName)));
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
        new ReviveCommandWorkflow(
                (playerId, identity) -> rt.submit(() -> rt.petService().revive(playerId, identity.petId(),
                        identity.tokenId(), identity.ownerId(), rt.soulItems())),
                action -> plugin.getServer().getScheduler().runTask(plugin, action),
                rt.messages()::send, rt::petName,
                failure -> plugin.getLogger().log(Level.WARNING, "NeoPets revive command failed", unwrap(failure)))
                .execute(sender, target, args[1]);
    }

    private void update(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, UPDATE, rt)) return;
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("check"))) {
            rt.messages().send(sender, MessageSpec.of("updater.usage"));
            return;
        }
        var settings = rt.configuration().current().updater();
        String version = plugin.getPluginMeta().getVersion();
        var directory = plugin.getServer().getUpdateFolderFile().toPath();
        String jarName = plugin.currentJarName();
        var result = updater.submit(rt::submitUpdate, () -> updater.run(settings,
                System.getenv("NEOPETS_GITHUB_TOKEN"), version, args.length == 2, () -> {
                    try (var input = plugin.getResource("neopets-update-public-key.pem")) {
                        return input == null ? null : input.readAllBytes();
                    } catch (java.io.IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                }, directory, jarName));
        complete(result, sender, outcome -> {
            switch (outcome) {
                case UpdateResult.Failure failure ->
                        rt.messages().send(sender, MessageSpec.of("updater." + failure.key()));
                case UpdateResult.Checked checked ->
                        rt.messages().send(sender, new MessageSpec("updater.checked", Map.of(
                                "installed", checked.installed(), "latest", checked.latest(),
                                "name", checked.name(), "notes", checked.notes(),
                                "status", rt.messages().plain(checked.newer() ? "updater.available" : "updater.current"))));
                case UpdateResult.Staged staged ->
                        rt.messages().send(sender, new MessageSpec("updater.staged", Map.of("version", staged.version())));
            }
        });
    }

    private void reload(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, RELOAD, rt)) {
            return;
        }
        if (args.length != 1) {
            rt.messages().send(sender, MessageSpec.of("general.usage-neopet"));
            return;
        }
        rt.adminConfiguration().reload().whenComplete((ignored, failure) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (failure == null) {
                        rt.messages().send(sender, MessageSpec.of("general.reloaded"));
                        return;
                    }
                    Throwable cause = unwrap(failure);
                    plugin.getLogger().log(Level.WARNING,
                            "NeoPets configuration reload failed: " + cause.getMessage(), cause);
                    rt.messages().send(sender, MessageSpec.of("errors.config-invalid"));
                }));
    }

    private void admin(NeoPetsRuntime rt, CommandSender sender, String[] args) {
        if (!allowed(sender, ADMIN, rt)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            rt.messages().send(sender, MessageSpec.of("errors.player-only"));
            return;
        }
        if (args.length != 1) {
            rt.messages().send(sender, MessageSpec.of("general.usage-neopet"));
            return;
        }
        rt.adminMenu().open(player);
    }

    private boolean allowed(CommandSender sender, String permission, NeoPetsRuntime rt) {
        if (CommandPermissions.has(sender, permission)) {
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
