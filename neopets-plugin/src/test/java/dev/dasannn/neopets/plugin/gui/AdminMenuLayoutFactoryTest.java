package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.FeatureRegistry;
import dev.dasannn.neopets.plugin.config.PluginSettings;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminMenuLayoutFactoryTest {
    @TempDir
    Path temporaryDirectory;

    private AdminMenuLayoutFactory factory;
    private PluginSettings settings;

    @BeforeEach
    void loadShippedConfiguration() throws Exception {
        var snapshot = new ConfigurationLoader(temporaryDirectory).load();
        factory = new AdminMenuLayoutFactory(snapshot.language());
        settings = snapshot.settings();
    }

    @Test
    void rendersTheApprovedFrameHeaderFeaturesAndControls() {
        var built = factory.build(settings, FeatureRegistry.features(), 0);
        MenuLayout layout = built.layout();

        assertEquals(6, layout.rows());
        assertEquals("COMMAND_BLOCK", layout.items().get(4).material());
        assertEquals("NeoPets · Administración", plain(layout.items().get(4).name()));
        assertEquals("BLACK_STAINED_GLASS_PANE", layout.items().get(0).material());
        assertEquals("TOTEM_OF_UNDYING", layout.items().get(10).material());
        assertEquals("Valor: ● Activado", plain(layout.items().get(10).lore().get(2)));
        assertEquals("Clic » Alternar", plain(layout.items().get(10).lore().getLast()));
        assertEquals("GOLD_INGOT", layout.items().get(15).material());
        assertEquals("Valor: 5000", plain(layout.items().get(15).lore().get(2)));
        assertTrue(plain(layout.items().get(15).lore().getLast()).contains("Shift » ×10"));
        assertEquals("BARRIER", layout.items().get(49).material());
        assertEquals("BOOK", layout.items().get(51).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(48).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(50).material());
        assertEquals(1, built.totalPages());
        assertEquals(FeatureRegistry.features().size(), Paging.CONTENT_SLOTS.stream().filter(layout.items()::containsKey).count());
    }

    @Test
    void rendersToggleStateInNameAndGlintAndReadableNumericRanges() {
        var layout = factory.build(settings, FeatureRegistry.features(), 0).layout();
        assertTrue(layout.items().get(10).glint());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                MiniMessage.miniMessage().deserialize(layout.items().get(10).name()).color());
        assertFalse(layout.items().get(11).glint());
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,
                MiniMessage.miniMessage().deserialize(layout.items().get(11).name()).color());
        assertEquals("Rango: 0 – sin límite", plain(layout.items().get(15).lore().get(3)));
        var cooldown = factory.build(settings,
                List.of(FeatureRegistry.find("abilities.zarpazo.cooldown-seconds").orElseThrow()), 0).layout();
        assertEquals("Rango: 1 s – 1 d", plain(cooldown.items().get(10).lore().get(3)));
    }

    @Test
    void minimumAbilityValuesKeepTheCurrentValueAndRangeReadable() throws Exception {
        var features = List.of("abilities.zarpazo.radius", "abilities.zarpazo.damage",
                        "abilities.vigia.radius", "abilities.buscatesoros.base-radius").stream()
                .map(path -> FeatureRegistry.find(path).orElseThrow()).toList();
        var configFile = temporaryDirectory.resolve("config.yml");
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile.toFile());
        for (var feature : features) {
            yaml.set(feature.path(), feature.type().apply(feature.currentValue(settings),
                    new FeatureRegistry.Interaction(false, true)));
            // Repeated right clicks reach the lower bound, including from the default radius.
            for (int click = 0; click < 4; click++) {
                yaml.set(feature.path(), feature.type().apply(yaml.get(feature.path()),
                        new FeatureRegistry.Interaction(false, true)));
            }
        }
        yaml.save(configFile.toFile());
        var minimumSettings = new ConfigurationLoader(temporaryDirectory).load().settings();
        var layout = factory.build(minimumSettings, features, 0).layout();
        for (int index = 0; index < features.size(); index++) {
            var item = layout.items().get(Paging.CONTENT_SLOTS.get(index));
            assertEquals("Valor: 0.5", plain(item.lore().get(2)));
            assertEquals("Rango: 0.5 – " + (index == 1 ? "2048" : "128"), plain(item.lore().get(3)));
        }
        MenuLayoutJson.write(Path.of(System.getProperty("neopets.preview.dir"))
                .resolve("admin-minimum-values.json"), layout, List.of(10, 11, 12, 13));
    }

    @Test
    void enforcesTheAdministrationPermission() {
        assertFalse(AdminMenu.canOpen(permission -> false));
        assertFalse(AdminMenu.canOpen(permission -> permission.equals("neopets.admin")));
        assertTrue(AdminMenu.canOpen(permission -> permission.equals(FeatureRegistry.GUI_PERMISSION)));
    }

    @Test
    void writesTheRequiredAdminPreviewAsJson() throws Exception {
        Path output = Path.of(System.getProperty("neopets.preview.dir"));
        MenuLayoutJson.write(output.resolve("admin-page-1.json"),
                factory.build(settings, FeatureRegistry.features(), 0).layout(), List.of(4, 10, 15, 22, 38, 51));
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
