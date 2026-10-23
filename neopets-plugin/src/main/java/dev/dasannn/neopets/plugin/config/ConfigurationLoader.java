package dev.dasannn.neopets.plugin.config;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.model.VariantAppearance;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.InMemoryVariantCatalog;
import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import dev.dasannn.neopets.platform.paper.effect.VanillaEffectCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemType;

/** Copies, parses and validates the complete NeoPets YAML configuration set. */
public final class ConfigurationLoader {
    private static final List<String> PET_TYPES = List.of("dog", "cat", "parrot", "polar_bear", "donkey");
    private static final List<String> DEFAULT_RESOURCES = defaultResources();
    private static final Pattern LOWERCASE_KEY = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");
    private static final Set<String> VERSION_2_MESSAGES = Set.of(
            "errors.profile-load", "general.usage-neopet", "general.usage-pet-action",
            "pets.already-active", "pets.already-inactive", "pets.not-owned", "pets.in-transit",
            "pets.dead-unavailable", "pets.list-header", "pets.list-entry", "pets.action-summon",
            "pets.action-dismiss", "skills.points-insufficient", "skills.points-overflow");
    private static final Set<String> VERSION_3_MESSAGES = Set.of(
            "gui.pets.blank", "gui.pets.header-name", "gui.pets.header-owned",
            "gui.pets.header-active", "gui.pets.header-sp", "gui.pets.header-hint",
            "gui.pets.empty-name", "gui.pets.empty-lore", "gui.pets.no-filter-matches-name",
            "gui.pets.no-filter-matches-lore", "gui.pets.pet-name", "gui.pets.pet-name-active",
            "gui.pets.pet-name-dead", "gui.pets.pet-subtitle", "gui.pets.age-adult",
            "gui.pets.age-baby", "gui.pets.state-active", "gui.pets.state-stored",
            "gui.pets.state-dead", "gui.pets.progress", "gui.pets.revive-line-one",
            "gui.pets.revive-line-two", "gui.pets.action-summon", "gui.pets.action-dismiss",
            "gui.pets.action-skills", "gui.pets.action-rename", "gui.pets.filter-name",
            "gui.pets.filter-current", "gui.pets.filter-action", "gui.pets.filter-all",
            "gui.pets.type-dog", "gui.pets.type-cat", "gui.pets.type-parrot",
            "gui.pets.type-polar-bear", "gui.pets.type-donkey", "gui.pets.previous-page",
            "gui.pets.next-page", "gui.pets.page-number", "gui.pets.close",
            "gui.pets.help-name", "gui.pets.help-lore", "gui.pets.help-message",
            "gui.pets.coming-soon", "gui.pets.rename-prompt", "gui.pets.rename-cancel-word",
            "gui.pets.rename-cancelled", "gui.pets.rename-invalid");
    private static final Set<String> VERSION_6_MESSAGES = Set.of(
            "gui.skill-tree.title", "gui.skill-tree.header-name", "gui.skill-tree.header-subtitle",
            "gui.skill-tree.header-available-sp", "gui.skill-tree.header-assigned-sp",
            "gui.skill-tree.header-nodes", "gui.skill-tree.page-number", "gui.skill-tree.next-hint",
            "gui.skill-tree.next-limit", "gui.skill-tree.next-complete",
            "gui.skill-tree.category-vanilla", "gui.skill-tree.category-custom",
            "gui.skill-tree.node-active-name", "gui.skill-tree.node-unlocked-name",
            "gui.skill-tree.node-next-name", "gui.skill-tree.node-locked-name",
            "gui.skill-tree.node-unavailable-name", "gui.skill-tree.node-sp-cost",
            "gui.skill-tree.node-money-cost", "gui.skill-tree.action-activate",
            "gui.skill-tree.action-unlock", "gui.skill-tree.reason-previous",
            "gui.skill-tree.reason-node-limit", "gui.skill-tree.reason-sp-limit",
            "gui.skill-tree.reason-no-sp", "gui.skill-tree.reason-no-money",
            "gui.skill-tree.reason-no-economy", "gui.skill-tree.previous-page",
            "gui.skill-tree.next-page", "gui.skill-tree.back", "gui.skill-tree.help-name",
            "gui.skill-tree.help-lore", "gui.skill-tree.help-message", "gui.skill-tree.reset-name",
            "gui.skill-tree.reset-cost", "gui.skill-tree.reset-refund", "gui.skill-tree.reset-action",
            "gui.skill-tree.reset-confirm-name", "gui.skill-tree.reset-confirm-action",
            "gui.skill-tree.attributes.health.name", "gui.skill-tree.attributes.health.description",
            "gui.skill-tree.attributes.health.effect", "gui.skill-tree.attributes.regeneration.name",
            "gui.skill-tree.attributes.regeneration.description",
            "gui.skill-tree.attributes.regeneration.effect",
            "gui.skill-tree.attributes.fire_resistance.name",
            "gui.skill-tree.attributes.fire_resistance.description",
            "gui.skill-tree.attributes.fire_resistance.effect",
            "gui.skill-tree.attributes.strength.name", "gui.skill-tree.attributes.strength.description",
            "gui.skill-tree.attributes.strength.effect", "gui.skill-tree.attributes.speed.name",
            "gui.skill-tree.attributes.speed.description", "gui.skill-tree.attributes.speed.effect",
            "gui.skill-tree.attributes.scent.name", "gui.skill-tree.attributes.scent.description",
            "gui.skill-tree.attributes.scent.effect", "gui.skill-tree.attributes.agility.name",
            "gui.skill-tree.attributes.agility.description", "gui.skill-tree.attributes.agility.effect",
            "gui.skill-tree.attributes.armor.name", "gui.skill-tree.attributes.armor.description",
            "gui.skill-tree.attributes.armor.effect", "gui.skill-tree.attributes.flight_speed.name",
            "gui.skill-tree.attributes.flight_speed.description",
            "gui.skill-tree.attributes.flight_speed.effect", "gui.skill-tree.attributes.alert_range.name",
            "gui.skill-tree.attributes.alert_range.description",
            "gui.skill-tree.attributes.alert_range.effect",
            "gui.skill-tree.attributes.knockback_resistance.name",
            "gui.skill-tree.attributes.knockback_resistance.description",
            "gui.skill-tree.attributes.knockback_resistance.effect",
            "gui.skill-tree.attributes.claw_swipe.name",
            "gui.skill-tree.attributes.claw_swipe.description",
            "gui.skill-tree.attributes.claw_swipe.effect",
            "gui.skill-tree.attributes.treasure_hunter.name",
            "gui.skill-tree.attributes.treasure_hunter.description",
            "gui.skill-tree.attributes.treasure_hunter.effect",
            "gui.skill-tree.attributes.feline_landing.name",
            "gui.skill-tree.attributes.feline_landing.description",
            "gui.skill-tree.attributes.feline_landing.effect", "gui.skill-tree.attributes.lookout.name",
            "gui.skill-tree.attributes.lookout.description", "gui.skill-tree.attributes.lookout.effect",
            "gui.skill-tree.attributes.courier_saddlebags.name",
            "gui.skill-tree.attributes.courier_saddlebags.description",
            "gui.skill-tree.attributes.courier_saddlebags.effect");
    private static final Set<String> VERSION_9_MESSAGES = Set.of(
            "pets.soul-delivered", "pets.soul-dropped");
    private static final Set<String> BASE_REQUIRED_MESSAGES = Set.of(
            "prefix", "errors.no-permission", "errors.player-only", "errors.player-not-found",
            "errors.invalid-amount", "errors.config-invalid", "errors.internal", "errors.profile-load",
            "general.reloaded",
            "general.usage-neopets", "general.usage-neopet", "general.usage-pet-action",
            "general.usage-give", "general.usage-sp", "general.usage-revive",
            "pets.none-owned", "pets.given", "pets.given-admin", "pets.already-owned", "pets.all-owned",
            "pets.unknown-variant", "pets.unknown-type", "pets.summoned", "pets.dismissed",
            "pets.active-limit", "pets.already-active", "pets.already-inactive", "pets.not-owned",
            "pets.in-transit", "pets.dead-unavailable", "pets.list-header", "pets.list-entry",
            "pets.action-summon", "pets.action-dismiss",
            "pets.renamed", "pets.rename-disabled", "pets.dead", "pets.revived",
            "pets.soul-required", "pets.soul-invalid", "pets.soul-item-name", "pets.soul-item-lore",
            "pets.shop-disabled", "skills.points", "skills.points-changed", "skills.node-unlocked",
            "skills.node-activated", "skills.previous-level-required", "skills.not-enough-money",
            "skills.not-enough-points", "skills.points-insufficient", "skills.points-overflow",
            "skills.node-limit", "skills.point-limit", "skills.reset",
            "skills.reset-disabled", "skills.reset-cost", "skills.already-unlocked", "skills.already-active",
            "courier.request-sent", "courier.request-received", "courier.accepted", "courier.rejected",
            "courier.returned", "courier.timed-out", "courier.cooldown", "courier.recipient-offline",
            "courier.unavailable", "courier.inventory-in-use", "courier.send", "courier.return",
            "gui.pets-title", "gui.skill-tree-title", "gui.admin-title", "gui.courier-title",
            "gui.previous-page", "gui.next-page", "gui.close", "gui.back", "gui.summoned",
            "gui.dismissed", "gui.dead", "gui.locked-node", "gui.unlocked-node", "gui.active-node",
            "gui.unavailable-node", "gui.rank-limits", "admin.feature-enabled", "admin.feature-disabled",
            "admin.value-updated",
            "gui.pets.blank", "gui.pets.header-name", "gui.pets.header-owned",
            "gui.pets.header-active", "gui.pets.header-sp", "gui.pets.header-hint",
            "gui.pets.empty-name", "gui.pets.empty-lore", "gui.pets.no-filter-matches-name",
            "gui.pets.no-filter-matches-lore",
            "gui.pets.pet-name", "gui.pets.pet-name-active", "gui.pets.pet-name-dead",
            "gui.pets.pet-subtitle", "gui.pets.age-adult", "gui.pets.age-baby",
            "gui.pets.state-active", "gui.pets.state-stored", "gui.pets.state-dead",
            "gui.pets.progress", "gui.pets.revive-line-one", "gui.pets.revive-line-two",
            "gui.pets.action-summon", "gui.pets.action-dismiss", "gui.pets.action-skills",
            "gui.pets.action-rename", "gui.pets.filter-name", "gui.pets.filter-current",
            "gui.pets.filter-action", "gui.pets.filter-all", "gui.pets.type-dog",
            "gui.pets.type-cat", "gui.pets.type-parrot", "gui.pets.type-polar-bear",
            "gui.pets.type-donkey", "gui.pets.previous-page", "gui.pets.next-page",
            "gui.pets.page-number",
            "gui.pets.close", "gui.pets.help-name", "gui.pets.help-lore", "gui.pets.help-message",
            "gui.pets.coming-soon", "gui.pets.rename-prompt", "gui.pets.rename-cancel-word",
            "gui.pets.rename-cancelled", "gui.pets.rename-invalid");
    private static final Set<String> REQUIRED_MESSAGES = requiredMessages();

    private static Set<String> requiredMessages() {
        var required = new java.util.HashSet<>(BASE_REQUIRED_MESSAGES);
        required.addAll(VERSION_6_MESSAGES);
        required.addAll(VERSION_9_MESSAGES);
        return Set.copyOf(required);
    }

    private final Path dataDirectory;
    private final ClassLoader resources;
    private final AppearanceValidator appearanceValidator;
    private final ConfigurationMigrator migrator;
    private final Set<String> knownVanillaEffectIds;

    /** Creates a loader backed by this class's bundled resources and permissive appearance validation. */
    public ConfigurationLoader(Path dataDirectory) {
        this(dataDirectory, ConfigurationLoader.class.getClassLoader(),
                AppearanceValidator.acceptingAll(), new ConfigurationMigrator(),
                VanillaEffectCatalog.knownEffectIds());
    }

    /** Creates a loader with the platform's appearance validator. */
    public ConfigurationLoader(Path dataDirectory, AppearanceValidator appearanceValidator) {
        this(dataDirectory, ConfigurationLoader.class.getClassLoader(),
                appearanceValidator, new ConfigurationMigrator(), VanillaEffectCatalog.knownEffectIds());
    }

    /** Creates a loader with explicit platform validators. */
    public ConfigurationLoader(
            Path dataDirectory,
            AppearanceValidator appearanceValidator,
            Set<String> knownVanillaEffectIds
    ) {
        this(dataDirectory, ConfigurationLoader.class.getClassLoader(), appearanceValidator,
                new ConfigurationMigrator(), knownVanillaEffectIds);
    }

    /** Creates a fully injectable loader for tests and future migration registrations. */
    public ConfigurationLoader(
            Path dataDirectory,
            ClassLoader resources,
            AppearanceValidator appearanceValidator,
            ConfigurationMigrator migrator
    ) {
        this(dataDirectory, resources, appearanceValidator, migrator,
                VanillaEffectCatalog.knownEffectIds());
    }

    /** Creates a fully injectable loader including the vanilla effect catalog identifiers. */
    public ConfigurationLoader(
            Path dataDirectory,
            ClassLoader resources,
            AppearanceValidator appearanceValidator,
            ConfigurationMigrator migrator,
            Set<String> knownVanillaEffectIds
    ) {
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.appearanceValidator = Objects.requireNonNull(appearanceValidator, "appearanceValidator");
        this.migrator = Objects.requireNonNull(migrator, "migrator");
        this.knownVanillaEffectIds = Set.copyOf(
                Objects.requireNonNull(knownVanillaEffectIds, "knownVanillaEffectIds"));
    }

    /** Copies missing defaults and returns a new immutable snapshot if the whole set is valid. */
    public ConfigurationSnapshot load() throws IOException, ConfigurationLoadException {
        copyMissingDefaults();
        var issues = new ArrayList<ConfigurationIssue>();

        YamlConfiguration root = loadYaml(Path.of("config.yml"), issues);
        MigrationOutcome migration = migrateRoot(root, issues);
        PluginSettings settings = parseSettings(root, issues);
        YamlConfiguration languageYaml = loadYaml(Path.of("lang", "es.yml"), issues);
        boolean languageChanged = backfillLanguage(languageYaml, migration.sourceVersion(), issues);

        var variants = new LinkedHashMap<VariantId, PetVariant>();
        var icons = new LinkedHashMap<VariantId, String>();
        var trees = new LinkedHashMap<PetTypeId, SkillTreeDefinition>();
        var migratedDocuments = new LinkedHashMap<Path, YamlConfiguration>();
        for (String type : PET_TYPES) {
            Path petPath = Path.of("pets", type + ".yml");
            YamlConfiguration pets = loadYaml(petPath, issues);
            if (migration.sourceVersion() > 0
                    && migrator.migratePet(pets, type, migration.sourceVersion())) {
                migratedDocuments.put(petPath, pets);
            }
            parseVariants(type, pets, variants, icons, issues);

            Path treePath = Path.of("skilltrees", type + ".yml");
            YamlConfiguration treeYaml = loadYaml(treePath, issues);
            if (migrator.migrateSkillTree(treeYaml, type, migration.sourceVersion())) {
                migratedDocuments.put(treePath, treeYaml);
            }
            parseSkillTree(type, treeYaml, trees, issues);
        }
        LanguageCatalog language = parseLanguage(languageYaml, issues);

        if (!issues.isEmpty()) {
            throw new ConfigurationLoadException(issues);
        }
        if (languageChanged) {
            languageYaml.save(dataDirectory.resolve(Path.of("lang", "es.yml")).toFile());
        }
        for (var migratedDocument : migratedDocuments.entrySet()) {
            migratedDocument.getValue().save(dataDirectory.resolve(migratedDocument.getKey()).toFile());
        }
        if (migration.changed()) {
            root.save(dataDirectory.resolve("config.yml").toFile());
        }
        return new ConfigurationSnapshot(settings, new InMemoryVariantCatalog(variants.values()), icons, trees, language);
    }

    private void copyMissingDefaults() throws IOException {
        for (String resource : DEFAULT_RESOURCES) {
            Path target = dataDirectory.resolve(resource);
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            try (InputStream input = resources.getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("bundled configuration resource is missing: " + resource);
                }
                Files.copy(input, target);
            }
        }
    }

    private YamlConfiguration loadYaml(Path relativePath, List<ConfigurationIssue> issues) {
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(
                Files.newInputStream(dataDirectory.resolve(relativePath)), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            issue(issues, relativePath, "<root>", "cannot parse YAML: " + conciseMessage(exception));
            return new YamlConfiguration();
        }
        return yaml;
    }

    private MigrationOutcome migrateRoot(YamlConfiguration root, List<ConfigurationIssue> issues) {
        Path file = Path.of("config.yml");
        Integer version = integer(root, file, "config-version", 1, Integer.MAX_VALUE, issues);
        if (version == null) {
            return new MigrationOutcome(-1, false);
        }
        try {
            return new MigrationOutcome(version, migrator.migrate(root, version));
        } catch (IllegalArgumentException exception) {
            issue(issues, file, "config-version", exception.getMessage());
            return new MigrationOutcome(version, false);
        }
    }

    private boolean backfillLanguage(
            YamlConfiguration language, int sourceVersion, List<ConfigurationIssue> issues
    ) {
        if (sourceVersion < 1 || sourceVersion >= ConfigurationMigrator.CURRENT_VERSION) {
            return false;
        }
        var defaults = new YamlConfiguration();
        try (InputStream input = resources.getResourceAsStream("lang/es.yml")) {
            if (input == null) {
                throw new IOException("bundled configuration resource is missing: lang/es.yml");
            }
            defaults.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            issue(issues, Path.of("lang", "es.yml"), "<root>",
                    "cannot load bundled defaults: " + conciseMessage(exception));
            return false;
        }

        boolean changed = sourceVersion < 2 && mergeMissing(language, defaults, VERSION_2_MESSAGES);
        if (sourceVersion < 3) {
            changed |= mergeMissing(language, defaults, VERSION_3_MESSAGES);
        }
        if (sourceVersion < 6) {
            changed |= mergeMissing(language, defaults, VERSION_6_MESSAGES);
        }
        if (sourceVersion < 9) {
            changed |= mergeMissing(language, defaults, VERSION_9_MESSAGES);
        }
        return changed;
    }

    private static boolean mergeMissing(
            YamlConfiguration language, YamlConfiguration defaults, Set<String> paths
    ) {
        boolean changed = false;
        for (String path : paths) {
            if (!language.isSet(path)) {
                language.set(path, defaults.get(path));
                changed = true;
            }
        }
        return changed;
    }

    private record MigrationOutcome(int sourceVersion, boolean changed) {
    }

    private PluginSettings parseSettings(YamlConfiguration root, List<ConfigurationIssue> issues) {
        Path file = Path.of("config.yml");
        int version = valueOr(integer(root, file, "config-version", 1, Integer.MAX_VALUE, issues), 1);
        String storageTypeText = valueOr(string(root, file, "storage.type", issues), "sqlite");
        PluginSettings.StorageType storageType = switch (storageTypeText.toLowerCase(Locale.ROOT)) {
            case "sqlite" -> PluginSettings.StorageType.SQLITE;
            case "mysql" -> PluginSettings.StorageType.MYSQL;
            default -> {
                issue(issues, file, "storage.type", "must be sqlite or mysql");
                yield PluginSettings.StorageType.SQLITE;
            }
        };
        String sqliteFile = valueOr(string(root, file, "storage.sqlite.file", issues), "neopets.db");
        String mysqlHost = valueOr(string(root, file, "storage.mysql.host", issues), "localhost");
        int mysqlPort = valueOr(integer(root, file, "storage.mysql.port", 1, 65_535, issues), 3306);
        String mysqlDatabase = valueOr(string(root, file, "storage.mysql.database", issues), "neopets");
        String mysqlUsername = valueOr(string(root, file, "storage.mysql.username", issues), "neopets");
        String mysqlPassword = valueOr(string(root, file, "storage.mysql.password", true, issues), "");

        boolean deathEnabled = valueOr(bool(root, file, "death.enabled", issues), true);
        boolean deathPvp = valueOr(bool(root, file, "death.pvp", issues), false);
        boolean deathKeepSkills = valueOr(bool(root, file, "death.keep-skills", issues), true);
        String soulMaterial = valueOr(string(root, file, "death.soul-item.material", issues), "ECHO_SHARD");
        Material parsedSoulMaterial = Material.matchMaterial(soulMaterial);
        if (!soulMaterial.equals(soulMaterial.toUpperCase(Locale.ROOT))
                || parsedSoulMaterial == null || !isInventoryItem(parsedSoulMaterial)) {
            issue(issues, file, "death.soul-item.material",
                    "must name a valid uppercase, non-air inventory item material");
        }
        String soulName = valueOr(string(root, file, "death.soul-item.name", issues),
                "<light_purple>Alma de <pet></light_purple>");
        List<String> soulLore = valueOr(stringList(root, file, "death.soul-item.lore", issues), List.of());
        validateMiniMessage(soulName, file, "death.soul-item.name", issues);
        for (int index = 0; index < soulLore.size(); index++) {
            validateMiniMessage(soulLore.get(index), file,
                    "death.soul-item.lore[" + index + "]", issues);
        }
        boolean shopEnabled = valueOr(bool(root, file, "shop.enabled", issues), false);
        boolean renameEnabled = valueOr(bool(root, file, "rename.enabled", issues), true);
        boolean resetEnabled = valueOr(bool(root, file, "reset.player-enabled", issues), false);
        BigDecimal resetCost = valueOr(decimal(root, file, "reset.cost", true, issues), BigDecimal.ZERO);
        double teleportDistance = valueOr(decimal(root, file, "follow.teleport-distance", false, issues),
                BigDecimal.valueOf(20)).doubleValue();

        int zarpazoCooldown = valueOr(integer(
                root, file, "abilities.zarpazo.cooldown-seconds", 1, 86_400, issues), 10);
        double zarpazoRadius = valueOr(boundedDecimal(
                root, file, "abilities.zarpazo.radius", BigDecimal.valueOf(128), issues),
                BigDecimal.valueOf(6)).doubleValue();
        double zarpazoDamage = valueOr(boundedDecimal(
                root, file, "abilities.zarpazo.damage", BigDecimal.valueOf(2_048), issues),
                BigDecimal.valueOf(4)).doubleValue();
        int vigiaCooldown = valueOr(integer(
                root, file, "abilities.vigia.cooldown-seconds", 1, 86_400, issues), 5);
        double vigiaRadius = valueOr(boundedDecimal(
                root, file, "abilities.vigia.radius", BigDecimal.valueOf(128), issues),
                BigDecimal.valueOf(16)).doubleValue();
        int vigiaDuration = valueOr(integer(
                root, file, "abilities.vigia.duration-seconds", 1, 3_600, issues), 5);

        var ranks = parseRanks(root, file, issues);
        int rankRefreshSeconds = valueOr(integer(
                root, file, "rank-resolution.bukkit-refresh-interval-seconds", 1, 86_400, issues), 30);
        long timeoutSeconds = valueOr(longInteger(root, file, "courier.request-timeout-seconds", 1, issues), 300L);
        long cooldownSeconds = valueOr(longInteger(root, file, "courier.cooldown-seconds", 0, issues), 900L);
        long loadTimeoutSeconds = valueOr(longInteger(
                root, file, "player-data.load-timeout-seconds", 1, issues), 10L);

        var mysql = new PluginSettings.Mysql(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword);
        var storage = new PluginSettings.Storage(storageType, sqliteFile, mysql);
        var soulItem = new PluginSettings.SoulItem(soulMaterial, soulName, soulLore);
        var death = new PluginSettings.Death(deathEnabled, deathPvp, deathKeepSkills, soulItem);
        var reset = new PluginSettings.Reset(resetEnabled, resetCost);
        var features = new PluginSettings.Features(death, shopEnabled, renameEnabled, reset);
        var abilities = new PluginSettings.Abilities(
                new PluginSettings.Zarpazo(
                        Duration.ofSeconds(zarpazoCooldown), zarpazoRadius, zarpazoDamage),
                new PluginSettings.Vigia(
                        Duration.ofSeconds(vigiaCooldown), vigiaRadius, Duration.ofSeconds(vigiaDuration)));
        return new PluginSettings(version, storage, features, teleportDistance, abilities, ranks,
                Duration.ofSeconds(rankRefreshSeconds),
                new PluginSettings.Courier(Duration.ofSeconds(timeoutSeconds), Duration.ofSeconds(cooldownSeconds)),
                Duration.ofSeconds(loadTimeoutSeconds));
    }

    private Map<String, RankLimits> parseRanks(
            YamlConfiguration root,
            Path file,
            List<ConfigurationIssue> issues
    ) {
        ConfigurationSection section = section(root, file, "ranks", issues);
        var ranks = new LinkedHashMap<String, RankLimits>();
        if (section != null) {
            for (String rank : section.getKeys(false)) {
                String base = "ranks." + rank;
                if (!LOWERCASE_KEY.matcher(rank).matches()) {
                    issue(issues, file, base, "rank id must be a lowercase key");
                }
                int active = valueOr(integer(root, file, base + ".max-active-pets", 0, Integer.MAX_VALUE, issues), 0);
                int points = valueOr(integer(root, file, base + ".max-skill-points", 0, Integer.MAX_VALUE, issues), 0);
                int nodes = valueOr(integer(root, file, base + ".max-nodes-per-pet", 0, Integer.MAX_VALUE, issues), 0);
                ranks.put(rank, new RankLimits(active, points, nodes));
            }
        }
        if (!ranks.containsKey("default")) {
            issue(issues, file, "ranks.default", "required rank is missing");
            ranks.put("default", new RankLimits(0, 0, 0));
        }
        return ranks;
    }

    private void parseVariants(
            String expectedType,
            YamlConfiguration yaml,
            Map<VariantId, PetVariant> variants,
            Map<VariantId, String> icons,
            List<ConfigurationIssue> issues
    ) {
        Path file = Path.of("pets", expectedType + ".yml");
        String typeText = valueOr(string(yaml, file, "type", issues), expectedType);
        if (!expectedType.equals(typeText)) {
            issue(issues, file, "type", "must match file name: " + expectedType);
        }
        PetTypeId type = petType(typeText, expectedType, file, "type", issues);
        ConfigurationSection entries = section(yaml, file, "variants", issues);
        if (entries == null) {
            return;
        }
        if (entries.getKeys(false).isEmpty()) {
            issue(issues, file, "variants", "must contain at least one variant");
        }
        for (String idText : entries.getKeys(false)) {
            String base = "variants." + idText;
            VariantId id = variantId(idText, file, base, issues);
            boolean baby = valueOr(bool(yaml, file, base + ".baby", issues), false);
            double weight = valueOr(decimal(yaml, file, base + ".weight", false, issues), BigDecimal.ONE).doubleValue();
            String displayName = valueOr(string(yaml, file, base + ".display-name", issues), idText);
            String icon = null;
            if (yaml.get(base + ".icon") != null) {
                icon = string(yaml, file, base + ".icon", issues);
                Material iconMaterial = icon == null ? null : Material.matchMaterial(icon);
                if (icon != null && (!icon.equals(icon.toUpperCase(Locale.ROOT))
                        || iconMaterial == null
                        || !isInventoryItem(iconMaterial))) {
                    issue(issues, file, base + ".icon",
                            "must name a valid uppercase, non-air inventory item material");
                }
            }
            VariantAppearance appearance = appearance(yaml, file, base + ".appearance", issues);
            List<String> appearanceFailures;
            try {
                appearanceFailures = List.copyOf(Objects.requireNonNull(
                        appearanceValidator.validate(type, appearance), "validator result"));
            } catch (RuntimeException exception) {
                appearanceFailures = List.of("appearance validator failed: " + conciseMessage(exception));
            }
            for (String reason : appearanceFailures) {
                issue(issues, file, base + ".appearance", reason);
            }
            try {
                PetVariant variant = new PetVariant(id, type, appearance, baby, weight, displayName);
                if (variants.putIfAbsent(id, variant) != null) {
                    issue(issues, file, base, "variant id is duplicated in another pet file");
                } else {
                    if (icon != null) {
                        icons.put(id, icon);
                    }
                }
            } catch (IllegalArgumentException exception) {
                issue(issues, file, base, exception.getMessage());
            }
        }
    }

    private VariantAppearance appearance(
            YamlConfiguration yaml,
            Path file,
            String path,
            List<ConfigurationIssue> issues
    ) {
        ConfigurationSection section = section(yaml, file, path, issues);
        if (section == null) {
            return VariantAppearance.empty();
        }
        var properties = new LinkedHashMap<String, String>();
        for (String key : section.getKeys(false)) {
            Object raw = section.get(key);
            if (raw instanceof String || raw instanceof Number || raw instanceof Boolean) {
                properties.put(key, String.valueOf(raw));
            } else {
                issue(issues, file, path + "." + key, "must be a scalar value");
            }
        }
        return new VariantAppearance(properties);
    }

    private void parseSkillTree(
            String expectedType,
            YamlConfiguration yaml,
            Map<PetTypeId, SkillTreeDefinition> trees,
            List<ConfigurationIssue> issues
    ) {
        Path file = Path.of("skilltrees", expectedType + ".yml");
        String typeText = valueOr(string(yaml, file, "type", issues), expectedType);
        if (!expectedType.equals(typeText)) {
            issue(issues, file, "type", "must match file name: " + expectedType);
        }
        PetTypeId type = petType(typeText, expectedType, file, "type", issues);
        ConfigurationSection entries = section(yaml, file, "attributes", issues);
        var attributes = new ArrayList<AttributeDefinition>();
        if (entries != null) {
            if (entries.getKeys(false).isEmpty()) {
                issue(issues, file, "attributes", "must contain at least one attribute");
            }
            for (String id : entries.getKeys(false)) {
                AttributeDefinition definition = parseAttribute(yaml, file, "attributes." + id, id, issues);
                if (definition != null) {
                    attributes.add(definition);
                }
            }
        }
        try {
            SkillTreeDefinition tree = new SkillTreeDefinition(type, attributes);
            if (trees.putIfAbsent(type, tree) != null) {
                issue(issues, file, "type", "pet type is duplicated in another skill-tree file");
            }
        } catch (IllegalArgumentException exception) {
            issue(issues, file, "attributes", exception.getMessage());
        }
    }

    private AttributeDefinition parseAttribute(
            YamlConfiguration yaml,
            Path file,
            String base,
            String id,
            List<ConfigurationIssue> issues
    ) {
        String categoryText = valueOr(string(yaml, file, base + ".category", issues), "vanilla");
        AttributeCategory category = switch (categoryText.toLowerCase(Locale.ROOT)) {
            case "vanilla" -> AttributeCategory.VANILLA;
            case "custom" -> AttributeCategory.CUSTOM;
            default -> {
                issue(issues, file, base + ".category", "must be vanilla or custom");
                yield AttributeCategory.VANILLA;
            }
        };
        int levels = valueOr(integer(yaml, file, base + ".max-level", 1, Integer.MAX_VALUE, issues), 1);
        String effect = valueOr(string(yaml, file, base + ".effect", issues), "invalid");
        if (category == AttributeCategory.VANILLA && !knownVanillaEffectIds.contains(effect)) {
            issue(issues, file, base + ".effect", "unknown vanilla effect id: " + effect);
        }
        List<BigDecimal> costs = decimalList(yaml, file, base + ".money-costs", levels, false, issues);
        List<BigDecimal> values = category == AttributeCategory.VANILLA
                ? decimalList(yaml, file, base + ".values", levels, true, issues)
                : List.of();
        SkillPointCostSpec skillPointCosts = skillPointCosts(yaml, file, base, levels, issues);
        if (costs == null || values == null || skillPointCosts == null) {
            return null;
        }
        try {
            return switch (category) {
                case VANILLA -> new AttributeDefinition(id, category, levels,
                        new VanillaEffectReference(effect, values), costs, skillPointCosts.forLevels(levels));
                case CUSTOM -> new AttributeDefinition(id, category, levels,
                        new CustomEffectReference(effect), costs, skillPointCosts.forLevels(levels));
            };
        } catch (IllegalArgumentException | NullPointerException exception) {
            issue(issues, file, base, conciseMessage(exception));
            return null;
        }
    }

    private SkillPointCostSpec skillPointCosts(
            YamlConfiguration yaml,
            Path file,
            String base,
            int levels,
            List<ConfigurationIssue> issues
    ) {
        Object list = yaml.get(base + ".skill-point-costs");
        if (list != null) {
            List<Integer> costs = integerList(yaml, file, base + ".skill-point-costs", levels, 0, issues);
            return costs == null ? null : new SkillPointCostSpec(costs, null);
        }
        Integer cost = integer(yaml, file, base + ".skill-point-cost", 0, Integer.MAX_VALUE, issues);
        return cost == null ? null : new SkillPointCostSpec(null, cost);
    }

    private LanguageCatalog parseLanguage(YamlConfiguration yaml, List<ConfigurationIssue> issues) {
        Path file = Path.of("lang", "es.yml");
        var messages = new LinkedHashMap<String, String>();
        MiniMessage strictMiniMessage = MiniMessage.builder().strict(true).build();
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                continue;
            }
            if (!(entry.getValue() instanceof String message)) {
                issue(issues, file, entry.getKey(), "message must be a MiniMessage string");
                continue;
            }
            if (message.isBlank()) {
                issue(issues, file, entry.getKey(), "message must not be blank");
                continue;
            }
            try {
                strictMiniMessage.deserialize(message);
                messages.put(entry.getKey(), message);
            } catch (RuntimeException exception) {
                issue(issues, file, entry.getKey(), "invalid MiniMessage: " + conciseMessage(exception));
            }
        }
        for (String key : REQUIRED_MESSAGES) {
            if (!messages.containsKey(key)) {
                issue(issues, file, key, "required message is missing");
            }
        }
        return new LanguageCatalog(messages);
    }

    private static ConfigurationSection section(
            ConfigurationSection root,
            Path file,
            String path,
            List<ConfigurationIssue> issues
    ) {
        Object raw = root.get(path);
        if (!(raw instanceof ConfigurationSection section)) {
            issue(issues, file, path, raw == null ? "required section is missing" : "must be a section");
            return null;
        }
        return section;
    }

    private static String string(ConfigurationSection root, Path file, String path,
                                 List<ConfigurationIssue> issues) {
        return string(root, file, path, false, issues);
    }

    private static String string(ConfigurationSection root, Path file, String path, boolean allowEmpty,
                                 List<ConfigurationIssue> issues) {
        Object raw = root.get(path);
        if (!(raw instanceof String value)) {
            issue(issues, file, path, raw == null ? "required string is missing" : "must be a string");
            return null;
        }
        if (!allowEmpty && value.isBlank()) {
            issue(issues, file, path, "must not be blank");
            return null;
        }
        return value;
    }

    private static Boolean bool(ConfigurationSection root, Path file, String path,
                                List<ConfigurationIssue> issues) {
        Object raw = root.get(path);
        if (!(raw instanceof Boolean value)) {
            issue(issues, file, path, raw == null ? "required boolean is missing" : "must be true or false");
            return null;
        }
        return value;
    }

    private static List<String> stringList(
            ConfigurationSection root, Path file, String path, List<ConfigurationIssue> issues
    ) {
        Object raw = root.get(path);
        if (!(raw instanceof List<?> values)) {
            issue(issues, file, path, raw == null ? "required string list is missing" : "must be a string list");
            return null;
        }
        var parsed = new ArrayList<String>(values.size());
        boolean valid = true;
        for (int index = 0; index < values.size(); index++) {
            if (!(values.get(index) instanceof String value) || value.isBlank()) {
                issue(issues, file, path + "[" + index + "]", "must be a non-blank string");
                valid = false;
            } else {
                parsed.add(value);
            }
        }
        return valid ? List.copyOf(parsed) : null;
    }

    private static void validateMiniMessage(
            String source, Path file, String path, List<ConfigurationIssue> issues
    ) {
        try {
            MiniMessage.builder().strict(true).build().deserialize(source);
        } catch (RuntimeException exception) {
            issue(issues, file, path, "invalid MiniMessage: " + conciseMessage(exception));
        }
    }

    private static Integer integer(ConfigurationSection root, Path file, String path, int minimum, int maximum,
                                   List<ConfigurationIssue> issues) {
        Object raw = root.get(path);
        if (!(raw instanceof Number number) || number.doubleValue() != number.intValue()) {
            issue(issues, file, path, raw == null ? "required integer is missing" : "must be an integer");
            return null;
        }
        int value = number.intValue();
        if (value < minimum || value > maximum) {
            issue(issues, file, path, "must be between " + minimum + " and " + maximum);
            return null;
        }
        return value;
    }

    private static Long longInteger(ConfigurationSection root, Path file, String path, long minimum,
                                    List<ConfigurationIssue> issues) {
        Object raw = root.get(path);
        if (!(raw instanceof Number number) || number.doubleValue() != number.longValue()) {
            issue(issues, file, path, raw == null ? "required integer is missing" : "must be an integer");
            return null;
        }
        long value = number.longValue();
        if (value < minimum) {
            issue(issues, file, path, "must be at least " + minimum);
            return null;
        }
        return value;
    }

    private static BigDecimal decimal(ConfigurationSection root, Path file, String path, boolean allowZero,
                                      List<ConfigurationIssue> issues) {
        Object raw = root.get(path);
        if (!(raw instanceof Number number)) {
            issue(issues, file, path, raw == null ? "required number is missing" : "must be a number");
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            issue(issues, file, path, "must be a finite number");
            return null;
        }
        if (!Double.isFinite(value.doubleValue())) {
            issue(issues, file, path, "must be a finite number");
            return null;
        }
        if (value.signum() < 0 || (!allowZero && value.signum() == 0)) {
            issue(issues, file, path, allowZero ? "must not be negative" : "must be greater than zero");
            return null;
        }
        return value;
    }

    private static BigDecimal boundedDecimal(
            ConfigurationSection root,
            Path file,
            String path,
            BigDecimal maximum,
            List<ConfigurationIssue> issues
    ) {
        BigDecimal value = decimal(root, file, path, false, issues);
        if (value != null && value.compareTo(maximum) > 0) {
            issue(issues, file, path, "must be at most " + maximum.toPlainString());
            return null;
        }
        return value;
    }

    private static List<BigDecimal> decimalList(
            ConfigurationSection root,
            Path file,
            String path,
            int expectedSize,
            boolean allowNegative,
            List<ConfigurationIssue> issues
    ) {
        Object raw = root.get(path);
        if (!(raw instanceof List<?> values)) {
            issue(issues, file, path, raw == null ? "required number list is missing" : "must be a number list");
            return null;
        }
        boolean valid = true;
        if (values.size() != expectedSize) {
            issue(issues, file, path, "must contain exactly " + expectedSize + " values");
            valid = false;
        }
        var parsed = new ArrayList<BigDecimal>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Number number)) {
                issue(issues, file, path + "[" + index + "]", "must be a number");
                valid = false;
                continue;
            }
            try {
                BigDecimal decimal = new BigDecimal(number.toString());
                if (!allowNegative && decimal.signum() < 0) {
                    issue(issues, file, path + "[" + index + "]", "must not be negative");
                    valid = false;
                }
                parsed.add(decimal);
            } catch (NumberFormatException exception) {
                issue(issues, file, path + "[" + index + "]", "must be a finite number");
                valid = false;
            }
        }
        return valid ? parsed : null;
    }

    private static List<Integer> integerList(
            ConfigurationSection root,
            Path file,
            String path,
            int expectedSize,
            int minimum,
            List<ConfigurationIssue> issues
    ) {
        Object raw = root.get(path);
        if (!(raw instanceof List<?> values)) {
            issue(issues, file, path, raw == null ? "required integer list is missing" : "must be an integer list");
            return null;
        }
        boolean valid = true;
        if (values.size() != expectedSize) {
            issue(issues, file, path, "must contain exactly " + expectedSize + " values");
            valid = false;
        }
        var parsed = new ArrayList<Integer>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Number number)
                    || number.doubleValue() != number.intValue()
                    || number.intValue() < minimum) {
                issue(issues, file, path + "[" + index + "]", "must be an integer of at least " + minimum);
                valid = false;
            } else {
                parsed.add(number.intValue());
            }
        }
        return valid ? parsed : null;
    }

    private record SkillPointCostSpec(List<Integer> perLevel, Integer uniform) {
        private List<Integer> forLevels(int levels) {
            return perLevel == null ? java.util.Collections.nCopies(levels, uniform) : perLevel;
        }
    }

    private static PetTypeId petType(
            String value,
            String fallback,
            Path file,
            String path,
            List<ConfigurationIssue> issues
    ) {
        try {
            return new PetTypeId(value);
        } catch (IllegalArgumentException exception) {
            issue(issues, file, path, exception.getMessage());
            return new PetTypeId(fallback);
        }
    }

    private static VariantId variantId(String value, Path file, String path, List<ConfigurationIssue> issues) {
        try {
            return new VariantId(value);
        } catch (IllegalArgumentException exception) {
            issue(issues, file, path, exception.getMessage());
            return new VariantId("invalid_" + Integer.toUnsignedString(value.hashCode()));
        }
    }

    private static boolean isInventoryItem(Material material) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return false;
        }
        try {
            ItemType.class.getField(material.name());
            return true;
        } catch (NoSuchFieldException exception) {
            return false;
        }
    }

    private static void issue(List<ConfigurationIssue> issues, Path file, String path, String reason) {
        issues.add(new ConfigurationIssue(file, path,
                reason == null || reason.isBlank() ? "invalid value" : reason));
    }

    private static String conciseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message.lines().findFirst().orElse(message);
    }

    private static <T> T valueOr(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static List<String> defaultResources() {
        var resources = new ArrayList<String>();
        resources.add("config.yml");
        for (String type : PET_TYPES) {
            resources.add("pets/" + type + ".yml");
            resources.add("skilltrees/" + type + ".yml");
        }
        resources.add("lang/es.yml");
        return List.copyOf(resources);
    }
}
