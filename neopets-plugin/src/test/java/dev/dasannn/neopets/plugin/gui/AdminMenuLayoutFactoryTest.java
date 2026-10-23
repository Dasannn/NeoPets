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
        assertEquals("GOLD_INGOT", layout.items().get(16).material());
        assertEquals("Valor: 5000", plain(layout.items().get(16).lore().get(2)));
        assertTrue(plain(layout.items().get(16).lore().getLast()).contains("Shift » ×10"));
        assertEquals("BARRIER", layout.items().get(49).material());
        assertEquals("BOOK", layout.items().get(51).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(48).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(50).material());
        assertEquals(1, built.totalPages());
        assertEquals(24, Paging.CONTENT_SLOTS.stream().filter(layout.items()::containsKey).count());
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
        assertEquals("Rango: 0 – sin límite", plain(layout.items().get(16).lore().get(3)));
        var cooldown = factory.build(settings,
                List.of(FeatureRegistry.find("abilities.zarpazo.cooldown-seconds").orElseThrow()), 0).layout();
        assertEquals("Rango: 1 s – 1 d", plain(cooldown.items().get(10).lore().get(3)));
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
                factory.build(settings, FeatureRegistry.features(), 0).layout(), List.of(4, 10, 16, 22, 38, 51));
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
