package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import dev.dasannn.neopets.platform.paper.PetSpawner;
import dev.dasannn.neopets.platform.paper.effect.VanillaEffectCatalog;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigurationLoaderTest {
    private static final List<String> PET_TYPES = List.of("dog", "cat", "parrot", "polar_bear", "donkey");

    @TempDir
    Path temporaryDirectory;

    @Test
    void shippedDefaultsCopyAndParseIntoCompleteDomainCatalogs() throws Exception {
        ConfigurationSnapshot snapshot = new ConfigurationLoader(temporaryDirectory).load();

        assertEquals(9, snapshot.settings().configVersion());
        assertEquals(10, snapshot.settings().abilities().zarpazo().cooldown().toSeconds());
        assertEquals(6.0, snapshot.settings().abilities().zarpazo().radius());
        assertEquals(4.0, snapshot.settings().abilities().zarpazo().damage());
        assertEquals(5, snapshot.settings().abilities().vigia().cooldown().toSeconds());
        assertEquals(16.0, snapshot.settings().abilities().vigia().radius());
        assertEquals(5, snapshot.settings().abilities().vigia().duration().toSeconds());
        assertEquals(PluginSettings.StorageType.SQLITE, snapshot.settings().storage().type());
        assertFalse(snapshot.settings().features().shopEnabled());
        assertEquals(30, snapshot.settings().bukkitRankRefreshInterval().toSeconds());
        assertEquals("ECHO_SHARD",
                snapshot.settings().features().death().soulItem().material());
        assertEquals(2, snapshot.settings().features().death().soulItem().lore().size());
        assertEquals(5, snapshot.skillTrees().size());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("config.yml")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("lang/es.yml")));

        for (String type : PET_TYPES) {
            PetTypeId typeId = new PetTypeId(type);
            assertFalse(snapshot.variants().findByType(typeId).isEmpty(), type);
            assertEquals(22, snapshot.skillTree(typeId).orElseThrow().totalNodes(), type);
            assertTrue(snapshot.skillTree(typeId).orElseThrow().attributes().stream()
                    .flatMap(attribute -> attribute.skillPointCosts().stream())
                    .allMatch(cost -> cost == 1), type);
        }
        assertEquals(snapshot.variants().findAll().size(), snapshot.variantIcons().size());
        String usage = snapshot.language().find("general.usage-give").orElseThrow();
        assertEquals("Uso: /neopet give <jugador> <id|ids|random> [tipo]",
                PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(usage)));
    }

    @Test
    void everyShippedPetAppearancePassesTheRealPlatformAppliers() throws Exception {
        var spawner = new PetSpawner();
        ConfigurationSnapshot snapshot = new ConfigurationLoader(
                temporaryDirectory, spawner::validateAppearance).load();

        var issues = snapshot.variants().findAll().stream()
                .flatMap(variant -> spawner.validateAppearance(variant.type(), variant.appearance()).stream()
                        .map(reason -> variant.id() + ": " + reason))
                .toList();

        assertEquals(List.of(), issues);
        assertTrue(snapshot.variants().findByType(new PetTypeId("parrot")).stream()
                .noneMatch(variant -> variant.baby()));
    }

    @Test
    void everyShippedVanillaEffectIdExistsInThePlatformCatalog() throws Exception {
        ConfigurationSnapshot snapshot = new ConfigurationLoader(temporaryDirectory).load();

        var configuredEffectIds = snapshot.skillTrees().values().stream()
                .flatMap(tree -> tree.attributes().stream())
                .filter(attribute -> attribute.effect() instanceof VanillaEffectReference)
                .map(attribute -> ((VanillaEffectReference) attribute.effect()).catalogEffectId())
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(VanillaEffectCatalog.knownEffectIds().containsAll(configuredEffectIds));
    }

    @Test
    void everyShippedVanillaProgressionIsCumulativeAndNeverBelowItsEntityBase() throws Exception {
        ConfigurationSnapshot snapshot = new ConfigurationLoader(temporaryDirectory).load();
        var catalog = new VanillaEffectCatalog();

        for (var tree : snapshot.skillTrees().values()) {
            for (var attribute : tree.attributes()) {
                if (!(attribute.effect() instanceof VanillaEffectReference reference)) {
                    continue;
                }
                double previous = representativeBase(reference.catalogEffectId());
                var effect = catalog.find(reference.catalogEffectId()).orElseThrow();
                for (var configured : reference.valuesPerLevel()) {
                    double current = effect instanceof VanillaEffectCatalog.AttributeEffect attributeEffect
                            ? attributeEffect.valueFromBase(representativeBase(reference.catalogEffectId()), configured)
                            : configured.doubleValue();
                    assertTrue(current >= previous,
                            () -> tree.petTypeId() + ":" + attribute.id() + " is not cumulative");
                    previous = current;
                }
            }
        }

        var dogHealth = (VanillaEffectReference) snapshot.skillTree(new PetTypeId("dog"))
                .orElseThrow().attributes().stream()
                .filter(attribute -> attribute.id().equals("health"))
                .findFirst().orElseThrow().effect();
        assertEquals(List.of(2, 4, 6, 8, 10), dogHealth.valuesPerLevel().stream()
                .map(java.math.BigDecimal::intValueExact).toList());
    }

    @Test
    void unknownVanillaEffectIdIsReportedAtItsConfigurationPath() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("skilltrees/cat.yml", "effect: max_health", "effect: unsupported_health");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "skilltrees/cat.yml", "attributes.health.effect");
    }

    @Test
    void invalidPlatformAppearanceReloadKeepsPreviousSnapshotActive() throws Exception {
        var spawner = new PetSpawner();
        var manager = new ConfigurationManager(new ConfigurationLoader(
                temporaryDirectory, spawner::validateAppearance));
        ConfigurationSnapshot original = manager.current();
        replace("pets/cat.yml", "appearance: { type: tabby }", "appearance: { variant: tabby }");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, manager::reload);

        assertIssue(failure, "pets/cat.yml", "variants.cat_tabby.appearance");
        assertSame(original, manager.current());
    }

    @Test
    void brokenFilesReportAllDiscoverableErrorsWithFileAndPath() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "teleport-distance: 20", "teleport-distance: -1");
        replace("config.yml", "max-active-pets: 1", "max-active-pets: -2");
        replace("pets/dog.yml", "weight: 10", "weight: 0");
        replace("skilltrees/cat.yml", "max-level: 5", "max-level: 0");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertTrue(failure.issues().size() >= 4);
        assertIssue(failure, "config.yml", "follow.teleport-distance");
        assertIssue(failure, "config.yml", "ranks.default.max-active-pets");
        assertIssue(failure, "pets/dog.yml", "variants.dog_pale.weight");
        assertIssue(failure, "skilltrees/cat.yml", "attributes.health.max-level");
        assertTrue(failure.getMessage().contains("Invalid NeoPets configuration"));
    }

    @Test
    void bukkitRankRefreshIntervalMustBePositive() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "bukkit-refresh-interval-seconds: 30",
                "bukkit-refresh-interval-seconds: 0");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "config.yml", "rank-resolution.bukkit-refresh-interval-seconds");
    }

    @Test
    void abilitySettingsAreValidatedAtTheirConfigurationPaths() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "cooldown-seconds: 10", "cooldown-seconds: 0");
        replace("config.yml", "damage: 4.0", "damage: -1");
        replace("config.yml", "radius: 16.0", "radius: 129");
        replace("config.yml", "duration-seconds: 5", "duration-seconds: nope");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "config.yml", "abilities.zarpazo.cooldown-seconds");
        assertIssue(failure, "config.yml", "abilities.zarpazo.damage");
        assertIssue(failure, "config.yml", "abilities.vigia.radius");
        assertIssue(failure, "config.yml", "abilities.vigia.duration-seconds");
    }

    @Test
    void failedReloadLeavesPreviousSnapshotActive() throws Exception {
        var manager = new ConfigurationManager(new ConfigurationLoader(temporaryDirectory));
        ConfigurationSnapshot original = manager.current();
        replace("config.yml", "config-version: 9", "config-version: 99");

        assertThrows(ConfigurationLoadException.class, manager::reload);

        assertSame(original, manager.current());
    }

    @Test
    void successfulReloadPublishesEditedValuesWithoutMutatingPreviousSnapshot() throws Exception {
        var manager = new ConfigurationManager(new ConfigurationLoader(temporaryDirectory));
        ConfigurationSnapshot original = manager.current();
        replace("config.yml", "teleport-distance: 20", "teleport-distance: 42");

        ConfigurationSnapshot reloaded = manager.reload();

        assertNotSame(original, reloaded);
        assertSame(reloaded, manager.current());
        assertEquals(20.0, original.settings().followTeleportDistance());
        assertEquals(42.0, reloaded.settings().followTeleportDistance());
    }

    @Test
    void uncheckedYamlParserFailuresAreCollectedAndDoNotStopLaterFiles() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Files.writeString(temporaryDirectory.resolve("pets/dog.yml"), "\"\": bad\n");
        Files.writeString(temporaryDirectory.resolve("pets/cat.yml"), "value: !!int nope\n");
        replace("skilltrees/parrot.yml", "max-level: 5", "max-level: 0");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "pets/dog.yml", "<root>");
        assertIssue(failure, "pets/cat.yml", "<root>");
        assertIssue(failure, "skilltrees/parrot.yml", "attributes.health.max-level");
    }

    @Test
    void platformAppearanceValidatorContributesFailuresWithoutStoppingOtherValidation() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory,
                (type, appearance) -> appearance.properties().isEmpty()
                        ? List.of()
                        : List.of("unsupported test appearance"));

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.file().toString().equals("pets/dog.yml")
                        && issue.path().equals("variants.dog_pale.appearance")
                        && issue.reason().equals("unsupported test appearance")));
        assertTrue(failure.issues().stream().anyMatch(issue -> issue.file().toString().equals("pets/cat.yml")));
    }

    @Test
    void enormousMaxLevelReportsListLengthsWithoutAllocatingPadding() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("skilltrees/cat.yml", "max-level: 5", "max-level: 2147483647");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "skilltrees/cat.yml", "attributes.health.money-costs");
        assertIssue(failure, "skilltrees/cat.yml", "attributes.health.values");
    }

    @Test
    void variantIconsMustBeNonAirInventoryItems() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("pets/dog.yml", "icon: BONE", "icon: WATER");
        replace("pets/cat.yml", "icon: COD", "icon: AIR");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "pets/dog.yml", "variants.dog_pale.icon");
        assertIssue(failure, "pets/cat.yml", "variants.cat_tabby.icon");
    }

    @Test
    void soulItemMaterialAndMiniMessageAreValidatedAtTheirPaths() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "material: ECHO_SHARD", "material: WATER");
        replace("config.yml", "<gray>Tipo: <type></gray>", "<gray>Tipo: <type>");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "config.yml", "death.soul-item.material");
        assertIssue(failure, "config.yml", "death.soul-item.lore[0]");
    }

    @Test
    void versionEightInstallReceivesSoulItemSettingsAndDeliveryMessages() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 8");
        String configuration = Files.readString(temporaryDirectory.resolve("config.yml"));
        int soulItem = configuration.indexOf("  soul-item:\n");
        int shop = configuration.indexOf("\nshop:\n", soulItem);
        Files.writeString(temporaryDirectory.resolve("config.yml"),
                configuration.substring(0, soulItem) + configuration.substring(shop + 1));
        String language = Files.readString(temporaryDirectory.resolve("lang/es.yml"));
        language = language.replaceFirst("(?m)^  soul-delivered:.*\\R", "")
                .replaceFirst("(?m)^  soul-dropped:.*\\R", "");
        Files.writeString(temporaryDirectory.resolve("lang/es.yml"), language);

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertEquals("ECHO_SHARD", upgraded.settings().features().death().soulItem().material());
        assertTrue(upgraded.language().find("pets.soul-delivered").isPresent());
        assertTrue(upgraded.language().find("pets.soul-dropped").isPresent());
    }

    @Test
    void variantIconIsOptionalForMenuSpawnEggFallback() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("pets/dog.yml", "    icon: BONE\n", "");

        ConfigurationSnapshot snapshot = loader.load();

        assertFalse(snapshot.variantIcons().containsKey(new VariantId("dog_pale")));
    }

    @Test
    void everyShippedMessageIsRequired() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("lang/es.yml", "  pets-title: \"Mis mascotas\"\n", "");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "lang/es.yml", "gui.pets-title");
    }

    @Test
    void oldInstallBackfillsConfigAndLanguageWithoutOverwritingCustomValues() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 1");
        replace("config.yml", "  load-timeout-seconds: 10\n", "");
        replace("config.yml", "  bukkit-refresh-interval-seconds: 30\n", "");
        replace("config.yml", "teleport-distance: 20", "teleport-distance: 37");
        replace("lang/es.yml", "  profile-load: \"<red>No se pudieron cargar tus datos de NeoPets. Inténtalo de nuevo.</red>\"\n", "");
        replace("lang/es.yml", "    header-name: \"<gold><bold>Mis mascotas</bold></gold>\"\n", "");
        replace("lang/es.yml", "  usage-neopet: \"<gray>Usa <white>/neopet give|summon|dismiss|sp|reload</white>.</gray>\"",
                "  usage-neopet: \"mensaje personalizado\"");
        replace("lang/es.yml", "    close: \"<red>Cerrar</red>\"", "    close: \"cierre personalizado\"");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertEquals(37.0, upgraded.settings().followTeleportDistance());
        assertEquals(10, upgraded.settings().playerDataLoadTimeout().toSeconds());
        assertEquals(30, upgraded.settings().bukkitRankRefreshInterval().toSeconds());
        assertEquals("mensaje personalizado", upgraded.language().find("general.usage-neopet").orElseThrow());
        assertEquals("cierre personalizado", upgraded.language().find("gui.pets.close").orElseThrow());
        assertTrue(upgraded.language().find("errors.profile-load").isPresent());
        assertTrue(upgraded.language().find("gui.pets.header-name").isPresent());
        assertTrue(Files.readString(temporaryDirectory.resolve("config.yml")).contains("config-version: 9"));
        assertTrue(Files.readString(temporaryDirectory.resolve("config.yml"))
                .contains("bukkit-refresh-interval-seconds: 30"));
        assertTrue(Files.readString(temporaryDirectory.resolve("lang/es.yml")).contains("profile-load:"));
        assertTrue(Files.readString(temporaryDirectory.resolve("lang/es.yml")).contains("header-name:"));
    }

    @Test
    void versionSixInstallPreservesCustomBukkitRankRefreshInterval() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 6");
        replace("config.yml", "bukkit-refresh-interval-seconds: 30",
                "bukkit-refresh-interval-seconds: 17");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertEquals(17, upgraded.settings().bukkitRankRefreshInterval().toSeconds());
    }

    @Test
    void versionTwoInstallReceivesEveryPetsMenuMessageAndStillLoads() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 2");
        String language = Files.readString(temporaryDirectory.resolve("lang/es.yml"));
        int petsSection = language.indexOf("  pets:\n", language.indexOf("gui:\n"));
        int adminSection = language.indexOf("\nadmin:\n", petsSection);
        Files.writeString(temporaryDirectory.resolve("lang/es.yml"),
                language.substring(0, petsSection) + language.substring(adminSection + 1));

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertTrue(upgraded.language().find("gui.pets.header-name").isPresent());
        assertTrue(upgraded.language().find("gui.pets.rename-invalid").isPresent());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5})
    void preVersionSixInstallReceivesSkillTreeMenuMessagesAndStillLoads(int sourceVersion) throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: " + sourceVersion);
        String language = Files.readString(temporaryDirectory.resolve("lang/es.yml"));
        int skillTreeSection = language.indexOf("  skill-tree:\n", language.indexOf("gui:\n"));
        int adminSection = language.indexOf("\nadmin:\n", skillTreeSection);
        Files.writeString(temporaryDirectory.resolve("lang/es.yml"),
                language.substring(0, skillTreeSection) + language.substring(adminSection + 1));

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertTrue(upgraded.language().find("gui.skill-tree.header-name").isPresent());
        assertTrue(upgraded.language().find("gui.skill-tree.attributes.claw_swipe.effect").isPresent());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void oldInstallMigratesBundledSkillValuesButPreservesCustomProgressions(int sourceVersion) throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: " + sourceVersion);
        replace("skilltrees/dog.yml", "values: [2, 4, 6, 8, 10]", "values: [22, 24, 26, 28, 30]");
        replace("skilltrees/dog.yml", "values: [0.05, 0.1, 0.15, 0.2, 0.25]",
                "values: [0.32, 0.34, 0.36, 0.38, 0.4]");
        replace("skilltrees/dog.yml", "values: [4, 8, 12, 16, 20]", "values: [3, 6, 9, 12, 15]");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        var dog = upgraded.skillTree(new PetTypeId("dog")).orElseThrow();
        assertEquals(List.of(2, 4, 6, 8, 10), values(dog, "health"));
        assertEquals(List.of(0.05, 0.1, 0.15, 0.2, 0.25), values(dog, "speed"));
        assertEquals(List.of(3, 6, 9, 12, 15), values(dog, "scent"));
        assertEquals(List.of(3, 6, 9, 12, 15),
                values(loader.load().skillTree(new PetTypeId("dog")).orElseThrow(), "scent"));
    }

    @Test
    void versionThreeInstallMigratesPreviouslyShippedPetResourcesBeforePlatformValidation() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory, new PetSpawner()::validateAppearance);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 3");
        installLegacyPetResource("cat");
        installLegacyPetResource("parrot");
        replace("pets/cat.yml", "type: cat", "type: cat\ncustom-root: retained");
        replace("pets/cat.yml", "appearance: { variant: black }", "appearance: { type: black }");
        replace("pets/cat.yml", "weight: 10", "weight: 41");
        replace("pets/cat.yml", "display-name: \"Gato atigrado\"", "display-name: \"Nombre personalizado\"");
        replace("pets/cat.yml", "icon: COD", "icon: COD\n    custom-variant: retained");
        replace("pets/parrot.yml", "weight: 10", "weight: 37");
        replace("pets/parrot.yml", "display-name: \"Loro rojo y azul\"",
                "display-name: \"Loro personalizado\"");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertEquals(13, upgraded.variants().findByType(new PetTypeId("cat")).size());
        assertEquals(7, upgraded.variants().findByType(new PetTypeId("parrot")).size());
        assertTrue(upgraded.variants().findByType(new PetTypeId("parrot")).stream()
                .noneMatch(variant -> variant.baby()));
        String cat = Files.readString(temporaryDirectory.resolve("pets/cat.yml"));
        String parrot = Files.readString(temporaryDirectory.resolve("pets/parrot.yml"));
        assertTrue(cat.contains("type: tabby"));
        assertFalse(cat.contains("variant: tabby"));
        assertTrue(cat.contains("type: black"));
        assertTrue(cat.contains("custom-root: retained"));
        assertTrue(cat.contains("custom-variant: retained"));
        assertTrue(cat.contains("display-name: Nombre personalizado"));
        assertTrue(cat.contains("weight: 41"));
        assertTrue(parrot.contains("variant: red"));
        assertTrue(parrot.contains("variant: cyan"));
        assertFalse(parrot.contains("variant: red_blue"));
        assertFalse(parrot.contains("variant: yellow_blue"));
        assertTrue(parrot.contains("display-name: Loro personalizado"));
        assertTrue(parrot.contains("weight: 37"));

        loader.load();
        assertEquals(cat, Files.readString(temporaryDirectory.resolve("pets/cat.yml")));
        assertEquals(parrot, Files.readString(temporaryDirectory.resolve("pets/parrot.yml")));
    }

    @Test
    void versionThreeInstallMigratesPetsAndSkillTreesTogether() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory, new PetSpawner()::validateAppearance);
        loader.load();
        replace("config.yml", "config-version: 9", "config-version: 3");
        installLegacyPetResource("cat");
        installLegacyPetResource("parrot");
        replace("skilltrees/dog.yml", "values: [2, 4, 6, 8, 10]", "values: [22, 24, 26, 28, 30]");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(9, upgraded.settings().configVersion());
        assertEquals(List.of(2, 4, 6, 8, 10),
                values(upgraded.skillTree(new PetTypeId("dog")).orElseThrow(), "health"));
        assertTrue(Files.readString(temporaryDirectory.resolve("pets/cat.yml")).contains("type: tabby"));
        assertTrue(Files.readString(temporaryDirectory.resolve("pets/parrot.yml")).contains("variant: cyan"));
        assertEquals(List.of(2, 4, 6, 8, 10),
                values(loader.load().skillTree(new PetTypeId("dog")).orElseThrow(), "health"));
    }

    private void installLegacyPetResource(String type) throws IOException {
        String resource = "legacy-v3/pets/" + type + ".yml";
        try (InputStream input = ConfigurationLoaderTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(input != null, "missing test resource: " + resource);
            Files.copy(input, temporaryDirectory.resolve("pets/" + type + ".yml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void replace(String relativePath, String target, String replacement) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        String original = Files.readString(file);
        assertTrue(original.contains(target), "test fixture target not found: " + target);
        Files.writeString(file, original.replaceFirst(Pattern.quote(target), Matcher.quoteReplacement(replacement)));
    }

    private static double representativeBase(String effectId) {
        return switch (effectId) {
            case "max_health" -> 40.0;
            case "movement_speed", "flying_speed" -> 0.3;
            default -> 0.0;
        };
    }

    private static List<Number> values(
            dev.dasannn.neopets.core.skilltree.SkillTreeDefinition tree,
            String attributeId
    ) {
        return tree.attributes().stream()
                .filter(attribute -> attribute.id().equals(attributeId))
                .findFirst()
                .map(attribute -> (VanillaEffectReference) attribute.effect())
                .orElseThrow()
                .valuesPerLevel().stream()
                .<Number>map(value -> {
                    if (value.scale() <= 0) {
                        return Integer.valueOf(value.intValueExact());
                    }
                    return Double.valueOf(value.doubleValue());
                })
                .toList();
    }

    private static void assertIssue(ConfigurationLoadException failure, String file, String path) {
        assertTrue(failure.issues().stream().anyMatch(issue ->
                        issue.file().toString().equals(file) && issue.path().equals(path)),
                () -> "missing issue " + file + ":" + path + " in " + failure.issues());
    }
}
