package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.SkillPointService;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillTreeMenuDispatchTest {
    private static final PlayerId PLAYER_ID = new PlayerId(UUID.randomUUID());
    private static final PetId PET_ID = new PetId(UUID.randomUUID());
    private static final VariantId VARIANT_ID = new VariantId("polar_bear");

    @TempDir
    Path temporaryDirectory;

    @Test
    void dispatchesMutationsMessagesRefreshAndResetConfirmation() throws Exception {
        Path configurationDirectory = temporaryDirectory.resolve("configuration");
        var loader = new ConfigurationLoader(configurationDirectory);
        var configuration = new ConfigurationManager(loader);
        Path configFile = configurationDirectory.resolve("config.yml");
        Files.writeString(configFile, Files.readString(configFile)
                .replace("player-enabled: false", "player-enabled: true"));
        configuration.reload();

        var snapshot = configuration.current();
        SkillTreeDefinition definition = snapshot.skillTree(
                snapshot.variants().findById(VARIANT_ID).orElseThrow().type()).orElseThrow();
        var pet = new OwnedPet(PET_ID, PLAYER_ID, VARIANT_ID, Optional.of("Nieve"),
                PetStatus.ALIVE, false, Instant.EPOCH);
        var pets = new InMemoryPetRepository(pet);
        var progress = new InMemoryProgressRepository();
        var players = new InMemoryPlayerRepository(new PlayerProfile(PLAYER_ID, 3, List.of()));
        var economy = new FakeEconomy(100_000);
        var events = new DomainEventBus();
        var operations = new PlayerOperationQueue();
        var points = new SkillPointService(players, events, operations);
        var service = new SkillTreeService(pets, progress,
                type -> type.equals(definition.petTypeId()) ? Optional.of(definition) : Optional.empty(),
                variant -> variant.equals(VARIANT_ID) ? Optional.of(definition.petTypeId()) : Optional.empty(),
                economy, points, ignored -> new RankLimits(1, 22, 22), events, operations);

        var sentMessages = new ArrayList<Component>();
        Player player = player(sentMessages);
        Server server = server();
        Plugin plugin = plugin(server);
        var menuFramework = new Menu(server, Logger.getAnonymousLogger(), Runnable::run, ignored -> null);
        var backCalls = new AtomicInteger();
        var skillMenu = new SkillTreeMenu(plugin, menuFramework, configuration,
                new LocalizedMessages(configuration), pets, progress, points,
                ignored -> new RankLimits(1, 22, 22), economy, service,
                ignored -> backCalls.incrementAndGet(), Logger.getAnonymousLogger());
        var session = new RecordingSession();
        var state = new SkillTreeMenu.ViewState(PET_ID);

        skillMenu.handle(session, player, state, "skills:unlock:health");

        assertEquals(1, progress.current().attributes().get("health").unlockedLevel());
        assertEquals(1, economy.withdrawals);
        assertEquals(1, session.refreshes);
        assertTrue(lastMessage(sentMessages).contains("Has desbloqueado Vida nivel 1"));

        skillMenu.handle(session, player, state, "skills:activate:health");

        assertEquals(1, progress.current().attributes().get("health").activeLevel());
        assertEquals(2, players.profile.skillPointBalance());
        assertEquals(2, session.refreshes);
        assertTrue(lastMessage(sentMessages).contains("Has activado Vida nivel 1"));

        skillMenu.handle(session, player, state, SkillTreeMenuLayoutFactory.ACTION_RESET);

        assertEquals(1, progress.current().unlockedNodes());
        assertEquals(3, session.refreshes);
        assertEquals(1, economy.withdrawals);

        skillMenu.handle(session, player, state, SkillTreeMenuLayoutFactory.ACTION_RESET);

        assertEquals(0, progress.current().unlockedNodes());
        assertEquals(3, players.profile.skillPointBalance());
        assertEquals(2, economy.withdrawals);
        assertEquals(4, session.refreshes);
        assertTrue(lastMessage(sentMessages).contains("El árbol de Nieve fue reiniciado"));

        skillMenu.handle(session, player, state, SkillTreeMenuLayoutFactory.ACTION_HELP);
        skillMenu.handle(session, player, state, SkillTreeMenuLayoutFactory.ACTION_BACK);

        assertTrue(lastMessage(sentMessages).contains("Primero desbloquea nodos con dinero"));
        assertEquals(1, backCalls.get());
    }

    private static String lastMessage(List<Component> messages) {
        return PlainTextComponentSerializer.plainText().serialize(messages.getLast());
    }

    private static Player player(List<Component> messages) {
        return proxy(Player.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getUniqueId" -> PLAYER_ID.value();
            case "isOnline" -> true;
            case "sendMessage" -> {
                if (arguments != null) {
                    for (Object argument : arguments) {
                        if (argument instanceof Component component) {
                            messages.add(component);
                        }
                    }
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Server server() {
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (ignored, method, arguments) -> {
            if (method.getName().equals("runTask")) {
                ((Runnable) arguments[1]).run();
            }
            return defaultValue(method.getReturnType());
        });
        return proxy(Server.class, (ignored, method, arguments) ->
                method.getName().equals("getScheduler") ? scheduler : defaultValue(method.getReturnType()));
    }

    private static Plugin plugin(Server server) {
        return proxy(Plugin.class, (ignored, method, arguments) ->
                method.getName().equals("getServer") ? server : defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class RecordingSession implements Menu.Session {
        private int refreshes;

        @Override
        public void refresh(MenuLayout layout) {
            refreshes++;
        }

        @Override
        public void close() {
        }
    }

    private static final class InMemoryPetRepository implements PetRepository {
        private final OwnedPet pet;

        private InMemoryPetRepository(OwnedPet pet) {
            this.pet = pet;
        }

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            return CompletableFuture.completedFuture(Optional.of(pet).filter(found -> found.id().equals(petId)));
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(pet.owner().equals(ownerId) ? List.of(pet) : List.of());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet changed) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }
    }

    private static final class InMemoryProgressRepository implements TreeProgressRepository {
        private TreeProgress progress;

        @Override
        public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(progress)
                    .filter(found -> found.petId().equals(petId)));
        }

        @Override
        public CompletableFuture<Void> save(TreeProgress changed) {
            progress = changed;
            return CompletableFuture.completedFuture(null);
        }

        private TreeProgress current() {
            return progress;
        }
    }

    private static final class InMemoryPlayerRepository implements PlayerRepository {
        private PlayerProfile profile;

        private InMemoryPlayerRepository(PlayerProfile profile) {
            this.profile = profile;
        }

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
            return CompletableFuture.completedFuture(Optional.of(profile)
                    .filter(found -> found.playerId().equals(playerId)));
        }

        @Override
        public CompletableFuture<Void> save(PlayerProfile changed) {
            profile = changed;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeEconomy implements Economy {
        private double balance;
        private int withdrawals;

        private FakeEconomy(double balance) {
            this.balance = balance;
        }

        @Override
        public CompletableFuture<EconomyResult> has(PlayerId playerId, double amount) {
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount) {
            withdrawals++;
            balance -= amount;
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount) {
            balance += amount;
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }
    }
}
