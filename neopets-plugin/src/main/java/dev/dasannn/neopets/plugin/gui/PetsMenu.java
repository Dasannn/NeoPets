package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.SkillPointService;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.command.PetResultMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.config.ConfigurationSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

/** Loads live player state and coordinates interactions for the "Mis mascotas" menu. */
public final class PetsMenu {
    private static final Map<String, String> DEFAULT_ICONS = Map.of(
            "dog", "WOLF_SPAWN_EGG",
            "cat", "CAT_SPAWN_EGG",
            "parrot", "PARROT_SPAWN_EGG",
            "polar_bear", "POLAR_BEAR_SPAWN_EGG",
            "donkey", "DONKEY_SPAWN_EGG");

    private final Plugin plugin;
    private final Server server;
    private final Menu menu;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final PetRepository pets;
    private final TreeProgressRepository trees;
    private final SkillPointService skillPoints;
    private final RankLimitsResolver ranks;
    private final PetService petService;
    private final PetRenameListener renames;
    private final BiConsumer<Player, PetId> skillTreeOpener;
    private final BiConsumer<Player, PetId> courierOpener;
    private final Logger logger;

    public PetsMenu(
            Plugin plugin,
            Menu menu,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            PetRepository pets,
            TreeProgressRepository trees,
            SkillPointService skillPoints,
            RankLimitsResolver ranks,
            PetService petService,
            PetRenameListener renames,
            BiConsumer<Player, PetId> skillTreeOpener,
            BiConsumer<Player, PetId> courierOpener,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.trees = Objects.requireNonNull(trees, "trees");
        this.skillPoints = Objects.requireNonNull(skillPoints, "skillPoints");
        this.ranks = Objects.requireNonNull(ranks, "ranks");
        this.petService = Objects.requireNonNull(petService, "petService");
        this.renames = Objects.requireNonNull(renames, "renames");
        this.skillTreeOpener = Objects.requireNonNull(skillTreeOpener, "skillTreeOpener");
        this.courierOpener = Objects.requireNonNull(courierOpener, "courierOpener");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Opens the first unfiltered page, including a valid empty state. */
    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        var state = new ViewState();
        load(player).whenComplete((data, failure) -> onMain(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                fail(player, "load pets menu", failure);
                return;
            }
            var built = build(data, state);
            state.accept(built, data);
            menu.open(player, built.layout(),
                    (session, viewer, action, click) -> handle(session, viewer, state, action, click));
        }));
    }

    private void handle(Menu.Session session, Player player, ViewState state, String action, ClickType click) {
        switch (action) {
            case PetsMenuLayoutFactory.ACTION_CLOSE -> session.close();
            case PetsMenuLayoutFactory.ACTION_HELP -> messages.send(player, MessageSpec.of("gui.pets.help-message"));
            case PetsMenuLayoutFactory.ACTION_FILTER -> {
                state.filter = state.filter.next();
                state.page = 0;
                refresh(session, player, state);
            }
            case PetsMenuLayoutFactory.ACTION_PREVIOUS_PAGE -> {
                if (state.page > 0) {
                    state.page--;
                    refresh(session, player, state);
                }
            }
            case PetsMenuLayoutFactory.ACTION_NEXT_PAGE -> {
                if (state.page + 1 < state.totalPages) {
                    state.page++;
                    refresh(session, player, state);
                }
            }
            default -> {
                if (action.startsWith(PetsMenuLayoutFactory.ACTION_PET_PREFIX)) {
                    petAction(session, player, state, action, click);
                }
            }
        }
    }

    private void petAction(
            Menu.Session session, Player player, ViewState state, String action, ClickType click
    ) {
        PetId petId;
        try {
            petId = new PetId(java.util.UUID.fromString(
                    action.substring(PetsMenuLayoutFactory.ACTION_PET_PREFIX.length())));
        } catch (IllegalArgumentException failure) {
            logger.log(Level.WARNING, "Invalid pet menu action: " + action, failure);
            return;
        }
        PetMenuEntry entry = state.data == null ? null : state.data.pets().stream()
                .filter(candidate -> candidate.petId().equals(petId.toString())).findFirst().orElse(null);
        if (click == ClickType.SHIFT_RIGHT && entry != null && entry.courierSaddlebagsActive()) {
            courierOpener.accept(player, petId);
            return;
        }
        if (click.isShiftClick()) {
            if (configuration.current().settings().features().renameEnabled()) {
                renames.begin(player, petId);
            }
            return;
        }
        if (click.isRightClick()) {
            skillTreeOpener.accept(player, petId);
            return;
        }
        if (!click.isLeftClick() || state.loading) {
            return;
        }
        state.loading = true;
        var playerId = new PlayerId(player.getUniqueId());
        pets.findById(petId).thenCompose(found -> {
            OwnedPet pet = found.filter(candidate -> candidate.owner().equals(playerId))
                    .orElseThrow(() -> new IllegalArgumentException("pet is not owned by player"));
            if (pet.active()) {
                return petService.dismiss(playerId, petId).thenApply(result -> PetResultMessages.dismiss(
                        result, changed -> petName(changed, configuration.current())));
            }
            return petService.summon(playerId, petId).thenApply(result -> PetResultMessages.summon(
                    result, changed -> petName(changed, configuration.current())));
        }).whenComplete((result, failure) -> onMain(() -> {
            state.loading = false;
            if (failure != null) {
                fail(player, "change pet state", failure);
                return;
            }
            messages.send(player, result);
            refresh(session, player, state);
        }));
    }

    private void refresh(Menu.Session session, Player player, ViewState state) {
        if (state.loading) {
            return;
        }
        state.loading = true;
        load(player).whenComplete((data, failure) -> onMain(() -> {
            state.loading = false;
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                fail(player, "refresh pets menu", failure);
                return;
            }
            var built = build(data, state);
            state.accept(built, data);
            session.refresh(built.layout());
        }));
    }

    private CompletableFuture<PetsMenuData> load(Player player) {
        ConfigurationSnapshot snapshot = configuration.current();
        var playerId = new PlayerId(player.getUniqueId());
        RankLimits limits = ranks.resolve(playerId);
        CompletableFuture<Integer> balance = skillPoints.balance(playerId);
        return pets.findByOwner(playerId).thenCompose(owned -> {
            var entries = owned.stream().map(pet -> entry(snapshot, pet)).toList();
            return CompletableFuture.allOf(entries.toArray(CompletableFuture[]::new))
                    .thenCombine(balance, (ignored, points) -> new PetsMenuData(
                            entries.stream().map(CompletableFuture::join).toList(), points,
                            limits.maxActivePets(), limits.maxSkillPointsPerPet(), limits.maxNodesPerPet()));
        });
    }

    private CompletableFuture<PetMenuEntry> entry(ConfigurationSnapshot snapshot, OwnedPet pet) {
        PetVariant variant = snapshot.variants().findById(pet.variant())
                .orElseThrow(() -> new IllegalStateException("Unknown variant: " + pet.variant()));
        SkillTreeDefinition definition = snapshot.skillTree(variant.type())
                .orElseThrow(() -> new IllegalStateException("No skill tree for " + variant.type()));
        return trees.load(pet.id()).thenApply(progress -> new PetMenuEntry(
                pet.id().toString(), variant.type().value(), petName(pet, snapshot),
                iconMaterial(snapshot, variant),
                variant.baby(), pet.status(), pet.active(), progress.map(TreeProgress::unlockedNodes).orElse(0),
                progress.map(value -> value.assignedSkillPoints(definition)).orElse(0),
                progress.map(value -> value.attributes().get("courier_saddlebags"))
                        .map(attribute -> attribute.activeLevel() > 0).orElse(false)));
    }

    private PetsMenuLayoutFactory.BuiltLayout build(PetsMenuData data, ViewState state) {
        return new PetsMenuLayoutFactory(configuration.current().language()).build(data, state.filter, state.page,
                configuration.current().settings().features().renameEnabled());
    }

    private String petName(OwnedPet pet, ConfigurationSnapshot snapshot) {
        return pet.customName().orElseGet(() -> snapshot.variants().findById(pet.variant())
                .map(PetVariant::displayNameKey).orElse(pet.variant().value()));
    }

    static String iconMaterial(ConfigurationSnapshot snapshot, PetVariant variant) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(variant, "variant");
        return snapshot.variantIcons().getOrDefault(variant.id(), defaultIcon(variant.type()));
    }

    private static String defaultIcon(PetTypeId type) {
        String icon = DEFAULT_ICONS.get(type.value());
        if (icon == null) {
            throw new IllegalArgumentException("No default icon for pet type: " + type);
        }
        return icon;
    }

    private void fail(Player player, String operation, Throwable failure) {
        logger.log(Level.WARNING, "Could not " + operation + " for " + player.getUniqueId(), unwrap(failure));
        messages.send(player, MessageSpec.of("errors.internal"));
    }

    private void onMain(Runnable task) {
        server.getScheduler().runTask(plugin, task);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class ViewState {
        private PetFilter filter = PetFilter.ALL;
        private int page;
        private int totalPages = 1;
        private boolean loading;
        private PetsMenuData data;

        private void accept(PetsMenuLayoutFactory.BuiltLayout built, PetsMenuData data) {
            page = built.pageIndex();
            totalPages = built.totalPages();
            this.data = data;
        }
    }
}
