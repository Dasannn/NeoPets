package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.config.AdminConfigurationService;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.config.FeatureRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

/** Coordinates permission-safe asynchronous edits for the administration features menu. */
public final class AdminMenu {
    private final Plugin plugin;
    private final Server server;
    private final Menu menu;
    private final ConfigurationManager configuration;
    private final AdminConfigurationService configurationService;
    private final LocalizedMessages messages;
    private final Logger logger;

    public AdminMenu(
            Plugin plugin,
            Menu menu,
            ConfigurationManager configuration,
            AdminConfigurationService configurationService,
            LocalizedMessages messages,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.menu = Objects.requireNonNull(menu, "menu");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.configurationService = Objects.requireNonNull(configurationService, "configurationService");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Opens the first page when the player has {@code neopets.admin.gui}. */
    public void open(Player player) {
        Objects.requireNonNull(player, "player");
        if (!canOpen(player::hasPermission)) {
            messages.send(player, MessageSpec.of("errors.no-permission"));
            return;
        }
        var state = new ViewState();
        List<FeatureRegistry.Feature> visible = visibleFeatures(player::hasPermission);
        var built = build(visible, state.page);
        state.accept(built);
        menu.open(player, built.layout(),
                (session, viewer, action, click) -> handle(session, viewer, state, action, click));
    }

    static boolean canOpen(Predicate<String> permissionCheck) {
        return Objects.requireNonNull(permissionCheck, "permissionCheck")
                .test(FeatureRegistry.GUI_PERMISSION);
    }

    void handle(Menu.Session session, Player player, ViewState state, String action, ClickType click) {
        if (!canOpen(player::hasPermission)) {
            messages.send(player, MessageSpec.of("errors.no-permission"));
            session.close();
            return;
        }
        switch (action) {
            case AdminMenuLayoutFactory.ACTION_CLOSE -> session.close();
            case AdminMenuLayoutFactory.ACTION_HELP ->
                    messages.send(player, MessageSpec.of("gui.admin.help-message"));
            case AdminMenuLayoutFactory.ACTION_PREVIOUS_PAGE -> changePage(session, player, state, -1);
            case AdminMenuLayoutFactory.ACTION_NEXT_PAGE -> changePage(session, player, state, 1);
            default -> {
                if (action.startsWith(AdminMenuLayoutFactory.ACTION_FEATURE_PREFIX)) {
                    edit(session, player, state,
                            action.substring(AdminMenuLayoutFactory.ACTION_FEATURE_PREFIX.length()), click);
                }
            }
        }
    }

    private void changePage(Menu.Session session, Player player, ViewState state, int direction) {
        int next = state.page + direction;
        if (next < 0 || next >= state.totalPages || state.loading) {
            return;
        }
        state.page = next;
        refresh(session, player, state);
    }

    private void edit(Menu.Session session, Player player, ViewState state, String path, ClickType click) {
        if (state.loading) {
            return;
        }
        FeatureRegistry.Feature feature = FeatureRegistry.find(path).orElse(null);
        if (feature == null || !feature.isVisibleTo(player::hasPermission)) {
            messages.send(player, MessageSpec.of("errors.no-permission"));
            return;
        }
        boolean increase;
        if (feature.type() instanceof FeatureRegistry.BooleanToggle) {
            if (!click.isLeftClick() && !click.isRightClick()) {
                return;
            }
            increase = true;
        } else if (click.isLeftClick()) {
            increase = true;
        } else if (click.isRightClick()) {
            increase = false;
        } else {
            return;
        }

        state.loading = true;
        configurationService.edit(feature, new FeatureRegistry.Interaction(increase, click.isShiftClick()))
                .whenComplete((result, failure) -> onMain(() -> {
                    state.loading = false;
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        Throwable cause = unwrap(failure);
                        logger.log(Level.WARNING, "Could not edit NeoPets setting " + feature.path(), cause);
                        messages.send(player, MessageSpec.of(cause instanceof ConfigurationLoadException
                                || cause instanceof org.bukkit.configuration.InvalidConfigurationException
                                || cause instanceof IllegalArgumentException
                                ? "admin.edit-invalid" : "errors.internal"));
                        return;
                    }
                    sendSuccess(player, result);
                    refresh(session, player, state);
                }));
    }

    private void sendSuccess(Player player, AdminConfigurationService.EditResult result) {
        String displayName = messages.plain(result.feature().nameKey());
        if (result.currentValue() instanceof Boolean enabled) {
            messages.send(player, new MessageSpec(enabled
                    ? "admin.feature-enabled" : "admin.feature-disabled", Map.of("feature", displayName)));
            return;
        }
        FeatureRegistry.BoundedNumber number = (FeatureRegistry.BoundedNumber) result.feature().type();
        messages.send(player, new MessageSpec("admin.value-updated", Map.of(
                "setting", displayName,
                "value", number.format((Number) result.currentValue()))));
    }

    private void refresh(Menu.Session session, Player player, ViewState state) {
        if (!player.isOnline()) {
            return;
        }
        var built = build(visibleFeatures(player::hasPermission), state.page);
        state.accept(built);
        session.refresh(built.layout());
    }

    private AdminMenuLayoutFactory.BuiltLayout build(List<FeatureRegistry.Feature> features, int page) {
        var snapshot = configuration.current();
        return new AdminMenuLayoutFactory(snapshot.language()).build(snapshot.settings(), features, page);
    }

    private static List<FeatureRegistry.Feature> visibleFeatures(Predicate<String> permissionCheck) {
        return FeatureRegistry.features().stream()
                .filter(feature -> feature.isVisibleTo(permissionCheck)).toList();
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
        private int page;
        private int totalPages = 1;
        private boolean loading;

        private void accept(AdminMenuLayoutFactory.BuiltLayout built) {
            page = built.pageIndex();
            totalPages = built.totalPages();
        }
    }
}
