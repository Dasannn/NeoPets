package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.platform.paper.PetSpawner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationLoaderTest {
    private static final List<String> PET_TYPES = List.of("dog", "cat", "parrot", "polar_bear", "donkey");

    @TempDir
    Path temporaryDirectory;

    @Test
    void shippedDefaultsCopyAndParseIntoCompleteDomainCatalogs() throws Exception {
        ConfigurationSnapshot snapshot = new ConfigurationLoader(temporaryDirectory).load();

        assertEquals(3, snapshot.settings().configVersion());
        assertEquals(PluginSettings.StorageType.SQLITE, snapshot.settings().storage().type());
        assertFalse(snapshot.settings().features().shopEnabled());
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
    void failedReloadLeavesPreviousSnapshotActive() throws Exception {
        var manager = new ConfigurationManager(new ConfigurationLoader(temporaryDirectory));
        ConfigurationSnapshot original = manager.current();
        replace("config.yml", "config-version: 3", "config-version: 99");

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
        replace("config.yml", "config-version: 3", "config-version: 1");
        replace("config.yml", "  load-timeout-seconds: 10\n", "");
        replace("config.yml", "teleport-distance: 20", "teleport-distance: 37");
        replace("lang/es.yml", "  profile-load: \"<red>No se pudieron cargar tus datos de NeoPets. Inténtalo de nuevo.</red>\"\n", "");
        replace("lang/es.yml", "    header-name: \"<gold><bold>Mis mascotas</bold></gold>\"\n", "");
        replace("lang/es.yml", "  usage-neopet: \"<gray>Usa <white>/neopet give|summon|dismiss|sp|reload</white>.</gray>\"",
                "  usage-neopet: \"mensaje personalizado\"");
        replace("lang/es.yml", "    close: \"<red>Cerrar</red>\"", "    close: \"cierre personalizado\"");

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(3, upgraded.settings().configVersion());
        assertEquals(37.0, upgraded.settings().followTeleportDistance());
        assertEquals(10, upgraded.settings().playerDataLoadTimeout().toSeconds());
        assertEquals("mensaje personalizado", upgraded.language().find("general.usage-neopet").orElseThrow());
        assertEquals("cierre personalizado", upgraded.language().find("gui.pets.close").orElseThrow());
        assertTrue(upgraded.language().find("errors.profile-load").isPresent());
        assertTrue(upgraded.language().find("gui.pets.header-name").isPresent());
        assertTrue(Files.readString(temporaryDirectory.resolve("config.yml")).contains("config-version: 3"));
        assertTrue(Files.readString(temporaryDirectory.resolve("lang/es.yml")).contains("profile-load:"));
        assertTrue(Files.readString(temporaryDirectory.resolve("lang/es.yml")).contains("header-name:"));
    }

    @Test
    void versionTwoInstallReceivesEveryPetsMenuMessageAndStillLoads() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("config.yml", "config-version: 3", "config-version: 2");
        String language = Files.readString(temporaryDirectory.resolve("lang/es.yml"));
        int petsSection = language.indexOf("  pets:\n", language.indexOf("gui:\n"));
        int adminSection = language.indexOf("\nadmin:\n", petsSection);
        Files.writeString(temporaryDirectory.resolve("lang/es.yml"),
                language.substring(0, petsSection) + language.substring(adminSection + 1));

        ConfigurationSnapshot upgraded = loader.load();

        assertEquals(3, upgraded.settings().configVersion());
        assertTrue(upgraded.language().find("gui.pets.header-name").isPresent());
        assertTrue(upgraded.language().find("gui.pets.rename-invalid").isPresent());
    }

    private void replace(String relativePath, String target, String replacement) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        String original = Files.readString(file);
        assertTrue(original.contains(target), "test fixture target not found: " + target);
        Files.writeString(file, original.replaceFirst(Pattern.quote(target), Matcher.quoteReplacement(replacement)));
    }

    private static void assertIssue(ConfigurationLoadException failure, String file, String path) {
        assertTrue(failure.issues().stream().anyMatch(issue ->
                        issue.file().toString().equals(file) && issue.path().equals(path)),
                () -> "missing issue " + file + ":" + path + " in " + failure.issues());
    }
}
