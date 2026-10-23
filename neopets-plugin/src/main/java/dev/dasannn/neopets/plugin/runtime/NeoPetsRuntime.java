package dev.dasannn.neopets.plugin.runtime;

import dev.dasannn.neopets.api.NeoPetsProvider;
import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.pet.VariantCatalog;
import dev.dasannn.neopets.core.skilltree.SkillPointService;
import dev.dasannn.neopets.core.skilltree.SkillTreeRegistry;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import dev.dasannn.neopets.plugin.NeoPetsPlugin;
import dev.dasannn.neopets.plugin.api.NeoPetsApiImpl;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.config.PluginSettings;
import dev.dasannn.neopets.plugin.integration.EconomyProvider;
import dev.dasannn.neopets.plugin.integration.RankLimitsResolverFactory;
import dev.dasannn.neopets.plugin.listener.OrphanCleanupListener;
import dev.dasannn.neopets.plugin.listener.PlayerLifecycleListener;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.platform.paper.OrphanCleaner;
import dev.dasannn.neopets.platform.paper.PaperPetEntityGateway;
import dev.dasannn.neopets.platform.paper.PaperTaskScheduler;
import dev.dasannn.neopets.platform.paper.PetBrain;
import dev.dasannn.neopets.platform.paper.PetProtectionListener;
import dev.dasannn.neopets.platform.paper.PetSpawnData;
import dev.dasannn.neopets.platform.paper.PetSpawner;
import dev.dasannn.neopets.platform.paper.PetTicker;
import dev.dasannn.neopets.storage.JdbcPetRepository;
import dev.dasannn.neopets.storage.JdbcPlayerRepository;
import dev.dasannn.neopets.storage.JdbcTreeProgressRepository;
import dev.dasannn.neopets.storage.StorageBootstrap;
import dev.dasannn.neopets.storage.StorageSettings;
import dev.dasannn.neopets.storage.StorageType;
import dev.dasannn.neopets.storage.cache.CachingPetRepository;
import dev.dasannn.neopets.storage.cache.CachingPlayerRepository;
import dev.dasannn.neopets.storage.cache.CachingTreeProgressRepository;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/** Owns the complete manually wired NeoPets runtime. */
public final class NeoPetsRuntime implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private final RuntimeLifecycle lifecycle;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final PetService petService;
    private final PetService offlinePetService;
    private final SkillPointService skillPoints;
    private final SkillTreeService skillTrees;
    private final CachingPetRepository pets;
    private final PlayerLifecycleListener playerLifecycle;

    private NeoPetsRuntime(
            RuntimeLifecycle lifecycle,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            PetService petService,
            PetService offlinePetService,
            SkillPointService skillPoints,
            SkillTreeService skillTrees,
            CachingPetRepository pets,
            PlayerLifecycleListener playerLifecycle
    ) {
        this.lifecycle = lifecycle;
        this.configuration = configuration;
        this.messages = messages;
        this.petService = petService;
        this.offlinePetService = offlinePetService;
        this.skillPoints = skillPoints;
        this.skillTrees = skillTrees;
        this.pets = pets;
        this.playerLifecycle = playerLifecycle;
    }

    /** Builds and starts the runtime, rolling back already-started resources on failure. */
    public static NeoPetsRuntime start(NeoPetsPlugin plugin) throws IOException, ConfigurationLoadException {
        Objects.requireNonNull(plugin, "plugin");
        var lifecycle = new RuntimeLifecycle();
        try {
            var configuration = new ConfigurationManager(new ConfigurationLoader(plugin.getDataFolder().toPath()));
            var messages = new LocalizedMessages(configuration);

            var storage = lifecycle.started(new StorageBootstrap(storageSettings(plugin, configuration)));
            var jdbcPlayers = new JdbcPlayerRepository(storage);
            var jdbcPets = new JdbcPetRepository(storage);
            var jdbcTrees = new JdbcTreeProgressRepository(storage, petId -> jdbcPets.findById(petId).thenApply(pet -> {
                OwnedPet owned = pet.orElseThrow(() -> new IllegalStateException("Unknown pet: " + petId));
                PetVariant variant = configuration.current().variants().findById(owned.variant())
                        .orElseThrow(() -> new IllegalStateException("Unknown variant: " + owned.variant()));
                return configuration.current().skillTree(variant.type())
                        .orElseThrow(() -> new IllegalStateException("No skill tree for " + variant.type()));
            }));
            var cache = new PlayerDataCache(jdbcPlayers, jdbcPets, jdbcTrees);
            var players = new CachingPlayerRepository(jdbcPlayers, cache);
            var pets = new CachingPetRepository(jdbcPets, cache);
            var trees = new CachingTreeProgressRepository(jdbcTrees, cache);

            var entityPort = new DeferredPorts.EntityGateway();
            var economyPort = new DeferredPorts.EconomyPort();
            var rankPort = new DeferredPorts.Ranks();
            var events = new DomainEventBus((event, failure) -> plugin.getLogger().log(
                    Level.SEVERE, "Domain event listener failed for " + event.getClass().getSimpleName(), failure));
            var operations = new PlayerOperationQueue();
            VariantCatalog variants = liveVariants(configuration);
            SkillTreeRegistry treeRegistry = type -> configuration.current().skillTree(type);
            var petService = new PetService(variants, pets, entityPort, rankPort, events,
                    () -> ThreadLocalRandom.current().nextDouble(), Clock.systemUTC());
            var offlinePetService = new PetService(variants, jdbcPets, entityPort, rankPort, events,
                    () -> ThreadLocalRandom.current().nextDouble(), Clock.systemUTC());
            var skillPoints = new SkillPointService(players, events, operations);
            var skillTrees = new SkillTreeService(pets, trees, treeRegistry,
                    variantId -> variants.findById(variantId).map(PetVariant::type), economyPort,
                    skillPoints, rankPort, events, operations);

            var scheduler = new PaperTaskScheduler(plugin);
            var livePets = new LivePetRegistry();
            var gateway = new PaperPetEntityGateway(plugin.getServer(), scheduler, new PetSpawner(), livePets,
                    petId -> spawnData(pets, petId),
                    variantId -> variants.findById(variantId).orElseThrow(
                            () -> new IllegalStateException("Unknown variant: " + variantId)));
            entityPort.bind(gateway);
            var brain = new PetBrain(4.0, configuration.current().settings().followTeleportDistance(), 1.2);
            var ticker = lifecycle.started(new PetTicker(plugin.getServer(), scheduler, livePets, brain, gateway));
            ticker.start();
            var orphanCleaner = new OrphanCleaner();
            orphanCleaner.cleanLoadedEntities(plugin.getServer(), livePets.snapshot().keySet());

            java.util.concurrent.Executor mainThread = task -> scheduler.runOnMain(task);
            economyPort.bind(EconomyProvider.resolve(plugin.getServer(), mainThread, plugin.getLogger()));
            rankPort.bind(RankLimitsResolverFactory.create(plugin, configuration));

            NeoPetsProvider.register(new NeoPetsApiImpl(pets, players, trees, livePets, configuration));
            lifecycle.started((AutoCloseable) NeoPetsProvider::unregister);

            var playerLifecycle = new PlayerLifecycleListener(
                    cache, pets, gateway, configuration, messages, plugin.getLogger());
            var pluginManager = plugin.getServer().getPluginManager();
            pluginManager.registerEvents(playerLifecycle, plugin);
            pluginManager.registerEvents(new OrphanCleanupListener(orphanCleaner, livePets), plugin);
            pluginManager.registerEvents(new PetProtectionListener(), plugin);
            pluginManager.registerEvents(ticker, plugin);

            lifecycle.started((AutoCloseable) () -> {
                CompletableFuture<Void> despawns = CompletableFuture.allOf(livePets.snapshot().keySet().stream()
                        .map(gateway::despawn).toArray(CompletableFuture[]::new));
                awaitShutdown(despawns, cache.flushAll());
            });

            return new NeoPetsRuntime(lifecycle, configuration, messages, petService, offlinePetService,
                    skillPoints, skillTrees, pets, playerLifecycle);
        } catch (IOException | ConfigurationLoadException exception) {
            closeAfterFailure(lifecycle, exception);
            throw exception;
        } catch (RuntimeException | Error exception) {
            closeAfterFailure(lifecycle, exception);
            throw exception;
        }
    }

    public ConfigurationManager configuration() {
        return configuration;
    }

    public LocalizedMessages messages() {
        return messages;
    }

    public PetService petService() {
        return petService;
    }

    public PetService offlinePetService() {
        return offlinePetService;
    }

    public SkillPointService skillPoints() {
        return skillPoints;
    }

    public SkillTreeService skillTrees() {
        return skillTrees;
    }

    public CachingPetRepository pets() {
        return pets;
    }

    public PlayerLifecycleListener playerLifecycle() {
        return playerLifecycle;
    }

    /** Returns the configured or custom name displayed for an owned pet. */
    public String petName(OwnedPet pet) {
        return pet.customName().orElseGet(() -> configuration.current().variants().findById(pet.variant())
                .map(PetVariant::displayNameKey).orElse(pet.variant().value()));
    }

    @Override
    public void close() throws Exception {
        lifecycle.close();
    }

    private static VariantCatalog liveVariants(ConfigurationManager configuration) {
        return new VariantCatalog() {
            @Override
            public Optional<PetVariant> findById(VariantId variantId) {
                return configuration.current().variants().findById(variantId);
            }

            @Override
            public List<PetVariant> findAll() {
                return configuration.current().variants().findAll();
            }

            @Override
            public List<PetVariant> findByType(PetTypeId typeId) {
                return configuration.current().variants().findByType(typeId);
            }
        };
    }

    private static PetSpawnData spawnData(CachingPetRepository pets, PetId petId) {
        OwnedPet pet = pets.findById(petId).join()
                .orElseThrow(() -> new IllegalStateException("Unknown pet: " + petId));
        return new PetSpawnData(pet.owner().value(), pet.variant());
    }

    private static StorageSettings storageSettings(
            NeoPetsPlugin plugin, ConfigurationManager configuration
    ) {
        PluginSettings.Storage configured = configuration.current().settings().storage();
        StorageType type = configured.type() == PluginSettings.StorageType.SQLITE
                ? StorageType.SQLITE : StorageType.MYSQL;
        return new StorageSettings(type,
                plugin.getDataFolder().toPath().resolve(configured.sqliteFile()),
                configured.mysql().host(), configured.mysql().port(), configured.mysql().database(),
                configured.mysql().username(), configured.mysql().password(), 8, Duration.ofSeconds(10));
    }

    private static void closeAfterFailure(RuntimeLifecycle lifecycle, Throwable startupFailure) {
        try {
            lifecycle.close();
        } catch (Exception closeFailure) {
            startupFailure.addSuppressed(closeFailure);
        }
    }

    private static void awaitShutdown(
            CompletableFuture<Void> despawns, CompletableFuture<Void> flush
    ) throws Exception {
        Exception failure = null;
        try {
            despawns.get(SHUTDOWN_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (Exception exception) {
            failure = exception;
        }
        try {
            flush.get(SHUTDOWN_TIMEOUT.toNanos(), TimeUnit.NANOSECONDS);
        } catch (Exception exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
