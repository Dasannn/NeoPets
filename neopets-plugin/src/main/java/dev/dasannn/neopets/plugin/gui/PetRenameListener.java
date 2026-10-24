package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.pet.RenameResult;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/** Captures the next chat message after a shift-click and persists it as the pet name. */
public final class PetRenameListener implements Listener {
    private final Plugin plugin;
    private final Server server;
    private final PetService petService;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final Logger logger;
    private final Map<UUID, PetId> pending = new ConcurrentHashMap<>();

    public PetRenameListener(
            Plugin plugin,
            PetService petService,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            Logger logger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.petService = Objects.requireNonNull(petService, "petService");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Starts a one-message rename prompt and closes the menu. */
    public void begin(Player player, PetId petId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(petId, "petId");
        if (!configuration.current().settings().features().renameEnabled()) {
            messages.send(player, MessageSpec.of("pets.rename-disabled"));
            return;
        }
        pending.put(player.getUniqueId(), petId);
        var openInventory = player.getOpenInventory().getTopInventory();
        server.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory() == openInventory) {
                player.closeInventory();
            }
        });
        messages.send(player, MessageSpec.of("gui.pets.rename-prompt"));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PetId petId = pending.remove(event.getPlayer().getUniqueId());
        if (petId == null) {
            return;
        }
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        server.getScheduler().runTask(plugin, () -> rename(event.getPlayer(), petId, input));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private void rename(Player player, PetId petId, String input) {
        if (!configuration.current().settings().features().renameEnabled()) {
            messages.send(player, MessageSpec.of("pets.rename-disabled"));
            return;
        }
        var normalized = PetNameValidator.normalize(input);
        if (normalized.isEmpty()) {
            messages.send(player, MessageSpec.of("gui.pets.rename-invalid"));
            return;
        }
        String name = normalized.orElseThrow();
        String cancelWord = messages.plain("gui.pets.rename-cancel-word");
        if (name.equalsIgnoreCase(cancelWord)) {
            messages.send(player, MessageSpec.of("gui.pets.rename-cancelled"));
            return;
        }
        var owner = new PlayerId(player.getUniqueId());
        petService.rename(owner, petId, name).whenComplete((result, failure) ->
                server.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        logger.log(Level.WARNING, "Could not rename pet " + petId, unwrap(failure));
                        messages.send(player, MessageSpec.of("errors.internal"));
                    } else if (result instanceof RenameResult.Renamed) {
                        messages.send(player, new MessageSpec("pets.renamed", Map.of("name", name)));
                    } else {
                        messages.send(player, MessageSpec.of("pets.not-owned"));
                    }
                }));
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
