package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CourierMenuLayoutFactoryTest {
    @TempDir Path temporaryDirectory;
    private CourierMenuLayoutFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        factory = new CourierMenuLayoutFactory(
                new ConfigurationLoader(temporaryDirectory.resolve("config")).load().language());
    }

    @Test
    void chestViewsExposeExactlyTwentySevenStorageSlotsAndDistinctTransferActions() {
        List<MenuItem> contents = sampleContents();
        MenuLayout owner = factory.owner("Rocinante", contents);
        MenuLayout recipient = factory.recipient("Rocinante", contents);

        assertEquals(6, owner.rows());
        assertEquals(27, CourierMenuLayoutFactory.STORAGE_SLOTS.size());
        assertEquals("ENDER_PEARL", owner.items().get(47).material());
        assertEquals(CourierMenuLayoutFactory.ACTION_SEND, owner.items().get(47).actionId().orElseThrow());
        assertEquals("LEAD", recipient.items().get(47).material());
        assertEquals(CourierMenuLayoutFactory.ACTION_RETURN,
                recipient.items().get(47).actionId().orElseThrow());
        assertEquals("OAK_DOOR", owner.items().get(49).material());
        assertEquals("BARRIER", recipient.items().get(49).material());
    }

    @Test
    void pickerPagesOnlinePlayerHeadsAndExcludesNoOneAddedByCaller() {
        var players = new ArrayList<CourierMenuLayoutFactory.PlayerEntry>();
        for (int index = 0; index < 30; index++) {
            players.add(new CourierMenuLayoutFactory.PlayerEntry(new UUID(0, index + 1), "Jugador " + (index + 1)));
        }
        var first = factory.picker(players, 0);
        var second = factory.picker(players, 1);
        assertEquals(2, first.totalPages());
        assertEquals("PLAYER_HEAD", first.layout().items().get(10).material());
        assertTrue(first.layout().items().get(50).actionId().isPresent());
        assertEquals(2, Paging.CONTENT_SLOTS.stream().filter(second.layout().items()::containsKey).count());
    }

    @Test
    void writesTheThreeRequiredCourierPreviews() throws Exception {
        Path output = Path.of(System.getProperty("neopets.preview.dir"));
        MenuLayoutJson.write(output.resolve("courier-owner-chest.json"),
                factory.owner("Rocinante", sampleContents()), List.of(4, 10, 47));
        MenuLayoutJson.write(output.resolve("courier-recipient-chest.json"),
                factory.recipient("Rocinante", sampleContents()), List.of(4, 10, 47));
        var players = List.of(
                new CourierMenuLayoutFactory.PlayerEntry(new UUID(0, 1), "Alex"),
                new CourierMenuLayoutFactory.PlayerEntry(new UUID(0, 2), "Steve"),
                new CourierMenuLayoutFactory.PlayerEntry(new UUID(0, 3), "Marina"));
        MenuLayoutJson.write(output.resolve("courier-player-picker.json"),
                factory.picker(players, 0).layout(), List.of(4, 10, 11, 51));
    }

    private static List<MenuItem> sampleContents() {
        var contents = new ArrayList<MenuItem>();
        for (int index = 0; index < 27; index++) contents.add(null);
        contents.set(0, new MenuItem("DIAMOND", "<aqua>Diamante</aqua>", List.of(), false, 17,
                java.util.Optional.empty()));
        contents.set(8, new MenuItem("OAK_LOG", "<white>Tronco de roble</white>", List.of(), false, 32,
                java.util.Optional.empty()));
        contents.set(20, new MenuItem("GOLDEN_CARROT", "<gold>Zanahoria dorada</gold>", List.of(), false, 8,
                java.util.Optional.empty()));
        return contents;
    }
}
