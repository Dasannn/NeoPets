package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetTypeId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

        assertEquals(1, snapshot.settings().configVersion());
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
        replace("config.yml", "config-version: 1", "config-version: 99");

        assertThrows(ConfigurationLoadException.class, manager::reload);

        assertSame(original, manager.current());
    }

    @Test
    void platformAppearanceValidatorContributesFailuresWithoutStoppingOtherValidation() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory,
                (type, appearance) -> appearance.properties().containsKey("variant")
                        ? List.of("unsupported test appearance") : List.of());

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
    void everyShippedMessageIsRequired() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        replace("lang/es.yml", "  pets-title: \"<dark_purple>Mis mascotas</dark_purple>\"\n", "");

        ConfigurationLoadException failure = assertThrows(ConfigurationLoadException.class, loader::load);

        assertIssue(failure, "lang/es.yml", "gui.pets-title");
    }

    private void replace(String relativePath, String target, String replacement) throws IOException {
        Path file = temporaryDirectory.resolve(relativePath);
        String original = Files.readString(file);
        assertTrue(original.contains(target), "test fixture target not found: " + target);
        Files.writeString(file, original.replaceFirst(target, replacement));
    }

    private static void assertIssue(ConfigurationLoadException failure, String file, String path) {
        assertTrue(failure.issues().stream().anyMatch(issue ->
                        issue.file().toString().equals(file) && issue.path().equals(path)),
                () -> "missing issue " + file + ":" + path + " in " + failure.issues());
    }
}
