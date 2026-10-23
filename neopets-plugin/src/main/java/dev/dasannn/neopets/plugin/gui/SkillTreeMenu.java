package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.SkillPointService;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.command.SkillTreeResultMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.config.ConfigurationSnapshot;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Loads live tree state and coordinates skill-tree menu interactions. */
public final class SkillTreeMenu {
    private final Plugin plugin;
    private final Server server;
    private final Menu menu;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final PetRepository pets;
    private final TreeProgressRepository trees;
    private final SkillPointService skillPoints;
    private final RankLimitsResolver ranks;
    private final Economy economy;
    private final SkillTreeService skillTrees;
    private final PaidSkillTreeReset paidReset;
    private final Consumer<Player> back;
    private final Logger logger;

    public SkillTreeMenu(
            Plugin plugin,
            Menu menu,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            PetRepository pets,
            TreeProgressRepository trees,
            SkillPointService skillPoints,
            RankLimitsResolver ranks,
            Economy economy,
            SkillTreeService skillTrees,
            Consumer<Player> back,
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
        this.economy = Objects.requireNonNull(economy, "economy");
        this.skillTrees = Objects.requireNonNull(skillTrees, "skillTrees");
        this.paidReset = new PaidSkillTreeReset(economy, skillTrees);
        this.back = Objects.requireNonNull(back, "back");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Opens the first page for an owned pet. */
    public void open(Player player, PetId petId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(petId, "petId");
        var state = new ViewState(petId);
        load(player, petId).whenComplete((data, failure) -> onMain(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                fail(player, "load skill tree", failure);
                return;
            }
            state.petName = data.petName();
            SkillTreeMenuLayoutFactory.BuiltLayout built = build(data, state);
            state.accept(built);
            menu.open(player, built.layout(),
                    (session, viewer, action, click) -> handle(session, viewer, state, action));
        }));
    }

    void handle(Menu.Session session, Player player, ViewState state, String actionId) {
        Optional<SkillTreeMenuAction> parsed = SkillTreeMenuAction.parse(actionId);
        if (parsed.isEmpty() || state.loading) {
            return;
        }
        switch (parsed.orElseThrow()) {
            case SkillTreeMenuAction.Back ignored -> back.accept(player);
            case SkillTreeMenuAction.Help ignored ->
                    messages.send(player, MessageSpec.of("gui.skill-tree.help-message"));
            case SkillTreeMenuAction.PreviousPage ignored -> {
                if (state.page > 0) {
                    state.page--;
                    state.resetConfirmation = false;
                    refresh(session, player, state);
                }
            }
            case SkillTreeMenuAction.NextPage ignored -> {
                if (state.page + 1 < state.totalPages) {
                    state.page++;
                    state.resetConfirmation = false;
                    refresh(session, player, state);
                }
            }
            case SkillTreeMenuAction.Unlock unlock -> mutate(session, player, state,
                    skillTrees.unlock(playerId(player), state.petId, unlock.attributeId())
                            .thenApply(result -> SkillTreeResultMessages.unlock(
                                    result, this::attributeName,
                                    pet -> petName(pet, configuration.current()))));
            case SkillTreeMenuAction.Activate activate -> mutate(session, player, state,
                    skillTrees.activate(playerId(player), state.petId, activate.attributeId())
                            .thenApply(result -> SkillTreeResultMessages.activate(
                                    result, this::attributeName,
                                    pet -> petName(pet, configuration.current()))));
            case SkillTreeMenuAction.Reset ignored -> reset(session, player, state);
        }
    }

    private void reset(Menu.Session session, Player player, ViewState state) {
        if (!configuration.current().settings().features().reset().playerEnabled()) {
            messages.send(player, MessageSpec.of("skills.reset-disabled"));
            return;
        }
        if (!state.resetConfirmation) {
            state.resetConfirmation = true;
            refresh(session, player, state);
            return;
        }
        BigDecimal cost = configuration.current().settings().features().reset().cost();
        mutate(session, player, state, paidReset.reset(playerId(player), state.petId, cost)
                .thenApply(result -> SkillTreeResultMessages.reset(result, state.petName)));
    }

    private void mutate(
            Menu.Session session,
            Player player,
            ViewState state,
            CompletableFuture<MessageSpec> operation
    ) {
        state.loading = true;
        operation.whenComplete((message, failure) -> onMain(() -> {
            state.loading = false;
            state.resetConfirmation = false;
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                fail(player, "change skill tree", failure);
                return;
            }
            messages.send(player, message);
            refresh(session, player, state);
        }));
    }

    private void refresh(Menu.Session session, Player player, ViewState state) {
        if (state.loading) {
            return;
        }
        state.loading = true;
        load(player, state.petId).whenComplete((data, failure) -> onMain(() -> {
            state.loading = false;
            if (!player.isOnline()) {
                return;
            }
            if (failure != null) {
                fail(player, "refresh skill tree", failure);
                return;
            }
            state.petName = data.petName();
            SkillTreeMenuLayoutFactory.BuiltLayout built = build(data, state);
            state.accept(built);
            session.refresh(built.layout());
        }));
    }

    private CompletableFuture<SkillTreeMenuData> load(Player player, PetId petId) {
        ConfigurationSnapshot snapshot = configuration.current();
        PlayerId playerId = playerId(player);
        RankLimits limits = ranks.resolve(playerId);
        return pets.findById(petId).thenCompose(found -> {
            OwnedPet pet = found.filter(candidate -> candidate.owner().equals(playerId))
                    .orElseThrow(() -> new IllegalArgumentException("pet is not owned by player"));
            PetVariant variant = snapshot.variants().findById(pet.variant())
                    .orElseThrow(() -> new IllegalStateException("Unknown variant: " + pet.variant()));
            SkillTreeDefinition definition = snapshot.skillTree(variant.type())
                    .orElseThrow(() -> new IllegalStateException("No skill tree for " + variant.type()));
            CompletableFuture<Optional<TreeProgress>> progress = trees.load(petId);
            CompletableFuture<Integer> points = skillPoints.balance(playerId);
            CompletableFuture<Optional<BigDecimal>> money = economyBalance(playerId, definition,
                    snapshot.settings().features().reset().cost());
            return CompletableFuture.allOf(progress, points, money).thenApply(ignored -> {
                TreeProgress treeProgress = progress.join().orElseGet(() -> TreeProgress.empty(petId, definition));
                return new SkillTreeMenuData(petId.toString(), petName(pet, snapshot), variant.type().value(),
                        PetsMenu.iconMaterial(snapshot, variant), variant.baby(), points.join(),
                        treeProgress.assignedSkillPoints(definition), limits.maxSkillPointsPerPet(),
                        treeProgress.unlockedNodes(), limits.maxNodesPerPet(), money.join(), definition,
                        treeProgress, snapshot.settings().features().reset().playerEnabled(),
                        snapshot.settings().features().reset().cost());
            });
        });
    }

    private CompletableFuture<Optional<BigDecimal>> economyBalance(
            PlayerId playerId, SkillTreeDefinition definition, BigDecimal resetCost
    ) {
        BigDecimal probe = definition.attributes().stream()
                .flatMap(attribute -> attribute.unlockMoneyCosts().stream())
                .filter(cost -> cost.signum() > 0)
                .findFirst()
                .orElse(resetCost);
        if (probe.signum() == 0) {
            return CompletableFuture.completedFuture(Optional.of(BigDecimal.ZERO));
        }
        double amount = probe.doubleValue();
        if (!Double.isFinite(amount)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return economy.has(playerId, amount).handle((result, failure) -> {
            if (failure != null || result instanceof EconomyResult.Failure) {
                return Optional.empty();
            }
            return Optional.of(BigDecimal.valueOf(((EconomyResult.Success) result).balance()));
        });
    }

    private SkillTreeMenuLayoutFactory.BuiltLayout build(SkillTreeMenuData data, ViewState state) {
        return new SkillTreeMenuLayoutFactory(configuration.current().language())
                .build(data, state.page, state.resetConfirmation);
    }

    private String attributeName(String attributeId) {
        return messages.plain("gui.skill-tree.attributes." + attributeId + ".name");
    }

    private static String petName(OwnedPet pet, ConfigurationSnapshot snapshot) {
        return pet.customName().orElseGet(() -> snapshot.variants().findById(pet.variant())
                .map(PetVariant::displayNameKey).orElse(pet.variant().value()));
    }

    private static PlayerId playerId(Player player) {
        return new PlayerId(player.getUniqueId());
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

    static final class ViewState {
        private final PetId petId;
        private String petName = "";
        private int page;
        private int totalPages = 1;
        private boolean resetConfirmation;
        private boolean loading;

        ViewState(PetId petId) {
            this.petId = petId;
        }

        private void accept(SkillTreeMenuLayoutFactory.BuiltLayout built) {
            page = built.pageIndex();
            totalPages = built.totalPages();
        }
    }
}
