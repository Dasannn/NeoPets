package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.runtime.RuntimeWorkTracker;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Loads online-player state and preserves active pets across connection boundaries. */
public final class PlayerLifecycleListener implements Listener {
    private final PlayerDataCache cache;
    private final PetRepository pets;
    private final PetEntityGateway entities;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final Logger logger;
    private final PendingLoginSessions pendingLogins;
    private final RuntimeWorkTracker work;
    private final ConcurrentHashMap<AsyncPlayerPreLoginEvent, Long> preLoginTokens = new ConcurrentHashMap<>();

    public PlayerLifecycleListener(
            PlayerDataCache cache,
            PetRepository pets,
            PetEntityGateway entities,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            Logger logger,
            Consumer<Runnable> scheduleLoginExpiry,
            RuntimeWorkTracker work
    ) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.pets = Objects.requireNonNull(pets, "pets");
        this.entities = Objects.requireNonNull(entities, "entities");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.work = Objects.requireNonNull(work, "work");
        pendingLogins = new PendingLoginSessions(scheduleLoginExpiry, this::cleanupRejectedLogin);
    }

    /** Blocks only Paper's asynchronous login thread until the complete profile is cached. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        var playerId = new PlayerId(event.getUniqueId());
        Duration timeout = configuration.current().settings().playerDataLoadTimeout();
        long token = pendingLogins.started(playerId);
        try {
            work.submit(() -> cache.load(playerId, token)).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            pendingLogins.loaded(playerId, token);
            preLoginTokens.put(event, token);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            pendingLogins.rejected(playerId, token);
            deny(event, playerId, exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            pendingLogins.rejected(playerId, token);
            deny(event, playerId, exception);
        }
    }

    /** Cleans a successfully loaded profile if a later asynchronous listener rejects the login. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginComplete(AsyncPlayerPreLoginEvent event) {
        Long token = preLoginTokens.remove(event);
        if (token != null && event.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
            pendingLogins.rejected(new PlayerId(event.getUniqueId()), token);
        }
    }

    /** Cleans a profile if the synchronous login phase rejects the player after pre-login. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLoginComplete(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            pendingLogins.rejected(new PlayerId(event.getPlayer().getUniqueId()));
        }
    }

    /** Recreates every entity whose persisted active flag survived the previous disconnect. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        PlayerId playerId = new PlayerId(event.getPlayer().getUniqueId());
        pendingLogins.joined(playerId);
        work.submit(() -> pets.findByOwner(playerId)
                .thenCompose(owned -> all(owned.stream().filter(OwnedPet::active)
                        .map(pet -> entities.spawn(pet.id())).toList())))
                .whenComplete((ignored, failure) -> logFailure("re-summon pets for " + playerId, failure));
    }

    /** Removes live entities without changing active flags, then evicts the cached profile. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerId playerId = new PlayerId(event.getPlayer().getUniqueId());
        Long sessionToken = pendingLogins.departed(playerId);
        work.submit(() -> continueAfter(despawn(playerId), () -> sessionToken == null
                        ? CompletableFuture.completedFuture(null) : cache.unload(playerId, sessionToken)))
                .whenComplete((ignored, failure) -> logFailure("unload player " + playerId, failure));
    }

    /** Despawns the currently active entities for one player without persisting state changes. */
    public CompletableFuture<Void> despawn(PlayerId playerId) {
        return pets.findByOwner(playerId).thenCompose(owned -> all(owned.stream()
                .filter(OwnedPet::active).map(pet -> entities.despawn(pet.id())).toList()));
    }

    private void deny(AsyncPlayerPreLoginEvent event, PlayerId playerId, Throwable failure) {
        logger.log(Level.WARNING, "Could not load NeoPets profile for " + playerId, unwrap(failure));
        event.disallow(PlayerPreLoginEvent.Result.KICK_OTHER,
                messages.render(MessageSpec.of("errors.profile-load")));
    }

    private void cleanupRejectedLogin(PlayerId playerId, long token, String reason) {
        work.submit(() -> cache.unload(playerId, token)).whenComplete((ignored, failure) ->
                logFailure(reason + " for " + playerId, failure));
    }

    private void logFailure(String operation, Throwable failure) {
        if (failure != null) {
            logger.log(Level.WARNING, "Could not " + operation, unwrap(failure));
        }
    }

    private static CompletableFuture<Void> all(List<CompletableFuture<Void>> futures) {
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static CompletableFuture<Void> continueAfter(
            CompletableFuture<Void> first,
            java.util.function.Supplier<CompletableFuture<Void>> second
    ) {
        return first.handle((ignored, firstFailure) -> firstFailure).thenCompose(firstFailure ->
                second.get().handle((ignored, secondFailure) -> {
                    if (firstFailure != null) {
                        Throwable cause = unwrap(firstFailure);
                        if (secondFailure != null) {
                            cause.addSuppressed(unwrap(secondFailure));
                        }
                        throw new CompletionException(cause);
                    }
                    if (secondFailure != null) {
                        throw new CompletionException(unwrap(secondFailure));
                    }
                    return null;
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
