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
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.VariantCatalog;
import dev.dasannn.neopets.core.port.PetIdGenerator;
import dev.dasannn.neopets.core.port.RankLimitsResolver;
import dev.dasannn.neopets.core.skilltree.SkillPointService;
import dev.dasannn.neopets.core.skilltree.SkillTreeRegistry;
import dev.dasannn.neopets.core.skilltree.SkillTreeService;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.plugin.NeoPetsPlugin;
import dev.dasannn.neopets.plugin.api.NeoPetsApiImpl;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.command.MessageSpec;
import dev.dasannn.neopets.plugin.config.ConfigurationLoadException;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.plugin.config.AdminConfigurationService;
import dev.dasannn.neopets.plugin.config.PluginSettings;
import dev.dasannn.neopets.plugin.integration.EconomyProvider;
import dev.dasannn.neopets.plugin.integration.NeoPetsPlaceholders;
import dev.dasannn.neopets.plugin.integration.PlaceholderApiIntegration;
import dev.dasannn.neopets.plugin.integration.PlaceholderApiRegistration;
import dev.dasannn.neopets.plugin.integration.RankLimitsResolverFactory;
import dev.dasannn.neopets.plugin.integration.RuntimePlaceholderDataSource;
import dev.dasannn.neopets.plugin.gui.Menu;
import dev.dasannn.neopets.plugin.gui.AdminMenu;
import dev.dasannn.neopets.plugin.gui.PetRenameListener;
import dev.dasannn.neopets.plugin.gui.PetsMenu;
import dev.dasannn.neopets.plugin.gui.SkillTreeMenu;
import dev.dasannn.neopets.plugin.listener.OrphanCleanupListener;
import dev.dasannn.neopets.plugin.listener.PetDeathListener;
import dev.dasannn.neopets.plugin.listener.PlayerLifecycleListener;
import dev.dasannn.neopets.plugin.listener.SoulDeliveryCoordinator;
import dev.dasannn.neopets.plugin.listener.SoulItemDelivery;
import dev.dasannn.neopets.plugin.listener.SoulItemFactory;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.platform.paper.OrphanCleaner;
import dev.dasannn.neopets.platform.paper.PaperPetEntityGateway;
import dev.dasannn.neopets.platform.paper.PaperTaskScheduler;
import dev.dasannn.neopets.platform.paper.PetBrain;
import dev.dasannn.neopets.platform.paper.PetDamagePolicy;
import dev.dasannn.neopets.platform.paper.PetProtectionListener;
import dev.dasannn.neopets.platform.paper.PetSpawnData;
import dev.dasannn.neopets.platform.paper.PetSpawner;
import dev.dasannn.neopets.platform.paper.PetTicker;
import dev.dasannn.neopets.platform.paper.ability.BuscatesorosAbility;
import dev.dasannn.neopets.platform.paper.ability.CooldownTracker;
import dev.dasannn.neopets.platform.paper.ability.DefaultAbilityRegistry;
import dev.dasannn.neopets.platform.paper.ability.FelineLandingAbility;
import dev.dasannn.neopets.platform.paper.ability.FelineLandingListener;
import dev.dasannn.neopets.platform.paper.ability.FelineLandingPolicy;
import dev.dasannn.neopets.platform.paper.ability.VigiaAbility;
import dev.dasannn.neopets.platform.paper.ability.ZarpazoAbility;
import dev.dasannn.neopets.platform.paper.effect.AttributeEffectAdapter;
import dev.dasannn.neopets.platform.paper.effect.PetSkillTreeState;
import dev.dasannn.neopets.platform.paper.effect.SkillTreePetEffectApplier;
import dev.dasannn.neopets.platform.paper.effect.VanillaEffectCatalog;
import dev.dasannn.neopets.storage.JdbcPetRepository;
import dev.dasannn.neopets.storage.JdbcPendingSoulDeliveryRepository;
import dev.dasannn.neopets.storage.JdbcPlayerRepository;
import dev.dasannn.neopets.storage.JdbcSoulTokenRepository;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/** Owns the complete manually wired NeoPets runtime. */
public final class NeoPetsRuntime implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(15);

    private final RuntimeLifecycle lifecycle;
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final PetService petService;
    private final SkillPointService skillPoints;
    private final SkillTreeService skillTrees;
    private final CachingPetRepository pets;
    private final PlayerLifecycleListener playerLifecycle;
    private final PetsMenu petsMenu;
    private final AdminMenu adminMenu;
    private final AdminConfigurationService adminConfiguration;
    private final PlayerDataCache cache;
    private final RuntimeWorkTracker work;

    private NeoPetsRuntime(
            RuntimeLifecycle lifecycle,
            ConfigurationManager configuration,
            LocalizedMessages messages,
            PetService petService,
            SkillPointService skillPoints,
            SkillTreeService skillTrees,
            CachingPetRepository pets,
            PlayerLifecycleListener playerLifecycle,
            PetsMenu petsMenu,
            AdminMenu adminMenu,
            AdminConfigurationService adminConfiguration,
            PlayerDataCache cache,
            RuntimeWorkTracker work
    ) {
        this.lifecycle = lifecycle;
        this.configuration = configuration;
        this.messages = messages;
        this.petService = petService;
        this.skillPoints = skillPoints;
        this.skillTrees = skillTrees;
        this.pets = pets;
        this.playerLifecycle = playerLifecycle;
        this.petsMenu = petsMenu;
        this.adminMenu = adminMenu;
        this.adminConfiguration = adminConfiguration;
        this.cache = cache;
        this.work = work;
    }

    /** Builds and starts the runtime, rolling back already-started resources on failure. */
    public static NeoPetsRuntime start(NeoPetsPlugin plugin) throws IOException, ConfigurationLoadException {
        Objects.requireNonNull(plugin, "plugin");
        var lifecycle = new RuntimeLifecycle();
        try {
            var spawner = new PetSpawner();
            var configurationLoader = new ConfigurationLoader(
                    plugin.getDataFolder().toPath(), spawner::validateAppearance,
                    VanillaEffectCatalog.knownEffectIds());
            var configuration = new ConfigurationManager(configurationLoader);
            var adminConfiguration = lifecycle.started(new AdminConfigurationService(
                    plugin.getDataFolder().toPath(), configurationLoader, configuration));
            var messages = new LocalizedMessages(configuration);

            var storage = lifecycle.started(new StorageBootstrap(storageSettings(plugin, configuration)));
            var jdbcPlayers = new JdbcPlayerRepository(storage);
            var jdbcPets = new JdbcPetRepository(storage);
            var jdbcSoulTokens = new JdbcSoulTokenRepository(storage);
            var pendingSoulDeliveries = new JdbcPendingSoulDeliveryRepository(storage);
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
            var work = new RuntimeWorkTracker();
            VariantCatalog variants = liveVariants(configuration);
            SkillTreeRegistry treeRegistry = type -> configuration.current().skillTree(type);
            var clock = Clock.systemUTC();
            var petService = new PetService(variants, pets, entityPort, rankPort, events,
                    () -> ThreadLocalRandom.current().nextDouble(), clock,
                    PetIdGenerator.randomUuid(), operations);
            var skillPoints = new SkillPointService(players, events, operations);
            var skillTrees = new SkillTreeService(pets, trees, treeRegistry,
                    variantId -> variants.findById(variantId).map(PetVariant::type), economyPort,
                    skillPoints, rankPort, events, operations);
            var soulItems = new SoulItemService(jdbcSoulTokens, clock);

            var scheduler = new PaperTaskScheduler(plugin);
            var livePets = new LivePetRegistry();
            var effectCatalog = new VanillaEffectCatalog();
            var felineLanding = new FelineLandingPolicy();
            var abilityRegistry = new DefaultAbilityRegistry(List.of(
                    new ZarpazoAbility(() -> zarpazoSettings(configuration)),
                    new VigiaAbility(() -> vigiaSettings(configuration)),
                    new BuscatesorosAbility(
                            () -> buscatesorosSettings(configuration),
                            (owner, count) -> owner.sendActionBar(messages.render(new MessageSpec(
                                    "abilities.buscatesoros.found", Map.of("count", Integer.toString(count)))))),
                    new FelineLandingAbility(felineLanding)),
                    new CooldownTracker(), Clock.systemUTC());
            var effectApplier = new SkillTreePetEffectApplier(
                    petId -> effectState(cache, configuration, petId),
                    new AttributeEffectAdapter(effectCatalog),
                    abilityRegistry,
                    abilityId -> plugin.getLogger().warning(
                            "No custom pet ability is registered for effect id '" + abilityId + "'"));
            var gateway = new PaperPetEntityGateway(plugin.getServer(), scheduler, spawner, livePets,
                    petId -> spawnData(cache, petId),
                    variantId -> variants.findById(variantId).orElseThrow(
                            () -> new IllegalStateException("Unknown variant: " + variantId)),
                    effectApplier);
            entityPort.bind(gateway);
            lifecycle.started(SkillTreeEffectRefresh.subscribe(events, livePets, gateway,
                    (petId, failure) -> plugin.getLogger().log(Level.SEVERE,
                            "Could not reapply skill-tree effects for pet " + petId, failure)));
            var brain = new PetBrain(4.0, configuration.current().settings().followTeleportDistance(), 1.2);
            var ticker = lifecycle.started(new PetTicker(
                    plugin.getServer(), scheduler, livePets, brain, gateway, abilityRegistry));
            ticker.start();
            var orphanCleaner = new OrphanCleaner();
            orphanCleaner.cleanLoadedEntities(plugin.getServer(), livePets.snapshot().keySet());

            economyPort.bind(EconomyProvider.resolve(plugin.getServer(), scheduler::runOnMain, plugin.getLogger()));
            rankPort.bind(RankLimitsResolverFactory.create(plugin, configuration));

            var playerLifecycle = new PlayerLifecycleListener(
                    cache, pets, gateway, configuration, messages, plugin.getLogger(), task ->
                    plugin.getServer().getScheduler().runTaskLater(plugin, task, 20L * 30), work);
            var pluginManager = plugin.getServer().getPluginManager();
            var placeholderData = new RuntimePlaceholderDataSource(
                    cache, livePets, rankPort, configuration,
                    plugin.getServer().getOnlinePlayers().stream().map(player -> player.getUniqueId()).toList());
            pluginManager.registerEvents(placeholderData, plugin);
            var placeholders = new NeoPetsPlaceholders(
                    placeholderData,
                    () -> configuration.current().language()
                            .find("placeholders.active-pet-empty").orElse("-"));
            var placeholderRegistration = PlaceholderApiRegistration.registerIfPresent(
                    pluginManager.isPluginEnabled("PlaceholderAPI"),
                    () -> registerPlaceholderApi(plugin, placeholders));
            placeholderRegistration.ifPresent(lifecycle::started);
            if (placeholderRegistration.isEmpty()) {
                plugin.getLogger().info("PlaceholderAPI is unavailable; placeholders are disabled");
            }
            pluginManager.registerEvents(playerLifecycle, plugin);
            pluginManager.registerEvents(new OrphanCleanupListener(orphanCleaner, livePets), plugin);
            var damagePolicy = new PetDamagePolicy(
                    () -> configuration.current().settings().features().death().enabled(),
                    () -> configuration.current().settings().features().death().pvp());
            pluginManager.registerEvents(new PetProtectionListener(damagePolicy), plugin);
            var soulDeliveries = new SoulDeliveryCoordinator(
                    plugin.getServer(), scheduler, pendingSoulDeliveries, pets,
                    new SoulItemFactory(configuration, messages), new SoulItemDelivery<>(), messages,
                    work, plugin.getLogger());
            pluginManager.registerEvents(soulDeliveries, plugin);
            pluginManager.registerEvents(new PetDeathListener(
                    petService, skillTrees, soulItems, soulDeliveries, livePets,
                    configuration, work, plugin.getLogger()), plugin);
            pluginManager.registerEvents(new FelineLandingListener(felineLanding), plugin);
            pluginManager.registerEvents(ticker, plugin);
            var menu = new Menu(plugin, plugin.getLogger());
            var adminMenu = new AdminMenu(plugin, menu, configuration, adminConfiguration,
                    messages, plugin.getLogger());
            var renames = new PetRenameListener(plugin, petService, configuration, messages, plugin.getLogger());
            var petsMenuReference = new AtomicReference<PetsMenu>();
            var skillTreeMenu = new SkillTreeMenu(plugin, menu, configuration, messages, pets, trees,
                    skillPoints, rankPort, economyPort, skillTrees,
                    player -> Objects.requireNonNull(petsMenuReference.get(), "pets menu").open(player),
                    plugin.getLogger());
            var petsMenu = new PetsMenu(plugin, menu, configuration, messages, pets, trees, skillPoints,
                    rankPort, petService, renames, skillTreeMenu::open, plugin.getLogger());
            petsMenuReference.set(petsMenu);
            pluginManager.registerEvents(menu, plugin);
            pluginManager.registerEvents(renames, plugin);

            lifecycle.started(new RuntimeShutdown(
                    work,
                    operations,
                    scheduler,
                    () -> CompletableFuture.allOf(livePets.snapshot().keySet().stream()
                            .map(gateway::despawn).toArray(CompletableFuture[]::new)),
                    cache::flushAll,
                    SHUTDOWN_TIMEOUT));

            NeoPetsProvider.register(new NeoPetsApiImpl(pets, players, trees, cache, livePets, configuration));
            lifecycle.started((AutoCloseable) NeoPetsProvider::unregister);

            return new NeoPetsRuntime(lifecycle, configuration, messages, petService,
                    skillPoints, skillTrees, pets, playerLifecycle, petsMenu, adminMenu,
                    adminConfiguration, cache, work);
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

    public PetsMenu petsMenu() {
        return petsMenu;
    }

    public AdminMenu adminMenu() {
        return adminMenu;
    }

    public AdminConfigurationService adminConfiguration() {
        return adminConfiguration;
    }

    /** Starts work only while the runtime is accepting operations and tracks it through completion. */
    public <T> CompletableFuture<T> submit(
            java.util.function.Supplier<? extends CompletableFuture<T>> operation
    ) {
        return work.submit(operation);
    }

    /** Returns the cached pets for an online player without falling through to persistence. */
    public List<OwnedPet> loadedPets(PlayerId playerId) {
        return cache.loadedPets(playerId).orElseGet(List::of);
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

    private static PetSpawnData spawnData(PlayerDataCache cache, PetId petId) {
        OwnedPet pet = cache.loadedPet(petId)
                .orElseThrow(() -> new IllegalStateException("Unknown pet: " + petId));
        return new PetSpawnData(pet.owner().value(), pet.variant());
    }

    private static Optional<PetSkillTreeState> effectState(
            PlayerDataCache cache,
            ConfigurationManager configuration,
            PetId petId
    ) {
        return cache.loadedPet(petId).flatMap(pet -> configuration.current().variants()
                .findById(pet.variant())
                .flatMap(variant -> configuration.current().skillTree(variant.type()))
                .map(definition -> new PetSkillTreeState(
                        definition,
                        cache.loadedTreeProgress(petId)
                                .orElseGet(() -> TreeProgress.empty(petId, definition)))));
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

    private static ZarpazoAbility.Settings zarpazoSettings(ConfigurationManager configuration) {
        PluginSettings.Zarpazo settings = configuration.current().settings().abilities().zarpazo();
        return new ZarpazoAbility.Settings(settings.cooldown(), settings.radius(), settings.damage());
    }

    private static VigiaAbility.Settings vigiaSettings(ConfigurationManager configuration) {
        PluginSettings.Vigia settings = configuration.current().settings().abilities().vigia();
        return new VigiaAbility.Settings(settings.cooldown(), settings.radius(), settings.duration());
    }

    private static BuscatesorosAbility.Settings buscatesorosSettings(ConfigurationManager configuration) {
        PluginSettings.Buscatesoros settings = configuration.current().settings().abilities().buscatesoros();
        return new BuscatesorosAbility.Settings(
                settings.cooldown(), settings.baseRadius(), settings.duration(),
                settings.maxTargets(), settings.maxScanBlocks(), settings.oreMaterials());
    }

    private static AutoCloseable registerPlaceholderApi(
            NeoPetsPlugin plugin,
            NeoPetsPlaceholders placeholders
    ) {
        return PlaceholderApiIntegration.register(plugin, placeholders);
    }

    private static void closeAfterFailure(RuntimeLifecycle lifecycle, Throwable startupFailure) {
        try {
            lifecycle.close();
        } catch (Exception closeFailure) {
            startupFailure.addSuppressed(closeFailure);
        }
    }
}
