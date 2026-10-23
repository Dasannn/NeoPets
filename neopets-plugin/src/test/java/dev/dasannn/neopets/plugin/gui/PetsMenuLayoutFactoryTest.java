package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PetsMenuLayoutFactoryTest {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    @TempDir
    Path temporaryDirectory;

    private PetsMenuLayoutFactory factory;

    @BeforeEach
    void loadShippedSpanishMenuText() throws Exception {
        factory = new PetsMenuLayoutFactory(
                new ConfigurationLoader(temporaryDirectory.resolve("configuration")).load().language());
    }

    @Test
    void emptyStateKeepsTheApprovedFrameHeaderAndControls() {
        var built = factory.build(data(List.of()), PetFilter.ALL, 0, true);
        MenuLayout layout = built.layout();

        assertEquals("Mis mascotas", plain(layout.title()));
        assertEquals(6, layout.rows());
        assertEquals("BLACK_STAINED_GLASS_PANE", layout.items().get(0).material());
        assertEquals("NETHER_STAR", layout.items().get(4).material());
        assertEquals("HOPPER", layout.items().get(47).material());
        assertEquals("BARRIER", layout.items().get(49).material());
        assertEquals("BOOK", layout.items().get(51).material());
        assertEquals("ENDER_EYE", layout.items().get(22).material());
        assertEquals("Aún no tienes mascotas", plain(layout.items().get(22).name()));
        assertEquals(List.of("Consíguelas completando dungeons"),
                layout.items().get(22).lore().stream().map(PetsMenuLayoutFactoryTest::plain).toList());
        assertEquals(1, built.totalPages());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(48).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(50).material());
        assertTrue(layout.items().get(48).actionId().isEmpty());
        assertTrue(layout.items().get(50).actionId().isEmpty());
        assertTrue(layout.items().get(4).lore().stream().map(PetsMenuLayoutFactoryTest::plain)
                .anyMatch("Página 1/1"::equals));
    }

    @Test
    void referenceStateShowsSummonedStoredBabyAndDeadPets() {
        MenuLayout layout = factory.build(referencePets(), PetFilter.ALL, 0, true).layout();

        MenuItem summoned = layout.items().get(10);
        assertEquals("PARROT_SPAWN_EGG", summoned.material());
        assertTrue(summoned.glint());
        assertEquals("Loro azul", plain(summoned.name()));
        assertEquals(List.of("Loro · Adulto", "", "Estado: ● Invocada", "Nodos: 4/10   SP: 2/3", "",
                "Clic izq. » Guardar", "Clic der. » Habilidades", "Shift+clic » Renombrar"),
                summoned.lore().stream().map(PetsMenuLayoutFactoryTest::plain).toList());

        assertEquals("Oso polar · Cría", plain(layout.items().get(12).lore().getFirst()));
        MenuItem dead = layout.items().get(13);
        assertEquals("GRAY_DYE", dead.material());
        assertEquals("Perro negro (muerto)", plain(dead.name()));
        assertTrue(dead.actionId().isEmpty());
        assertEquals("Estado: ✖ Muerta", plain(dead.lore().get(2)));
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(48).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", layout.items().get(50).material());
    }

    @Test
    void thirtyPetsFillPageOneAndContinueOnPageTwo() {
        PetsMenuData data = data(thirtyPets());
        var first = factory.build(data, PetFilter.ALL, 0, true);
        var second = factory.build(data, PetFilter.ALL, 1, true);

        assertEquals(28, contentCount(first.layout()));
        assertEquals(2, contentCount(second.layout()));
        assertEquals("Mascota 28", plain(first.layout().items().get(43).name()));
        assertEquals("Mascota 29", plain(second.layout().items().get(10).name()));
        assertEquals("Mascota 30", plain(second.layout().items().get(11).name()));
        assertTrue(first.layout().items().get(50).actionId().isPresent());
        assertTrue(second.layout().items().get(48).actionId().isPresent());
        assertTrue(second.layout().items().get(50).actionId().isEmpty());
        assertEquals("GRAY_STAINED_GLASS_PANE", first.layout().items().get(48).material());
        assertEquals("GRAY_STAINED_GLASS_PANE", second.layout().items().get(50).material());
        assertEquals(List.of("Página 1/2"), first.layout().items().get(50).lore().stream()
                .map(PetsMenuLayoutFactoryTest::plain).toList());
        assertEquals(List.of("Página 2/2"), second.layout().items().get(48).lore().stream()
                .map(PetsMenuLayoutFactoryTest::plain).toList());
        assertTrue(second.layout().items().get(4).lore().stream().map(PetsMenuLayoutFactoryTest::plain)
                .anyMatch("Página 2/2"::equals));
    }

    @Test
    void parrotFilterRemovesOtherTypesAndClampsThePage() {
        var pets = List.of(
                pet(1, "dog", "Perro", "WOLF_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0),
                pet(2, "parrot", "Loro rojo", "PARROT_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0),
                pet(3, "cat", "Gato", "CAT_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0),
                pet(4, "parrot", "Loro verde", "PARROT_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0));

        var built = factory.build(data(pets), PetFilter.PARROT, 3, false);

        assertEquals(0, built.pageIndex());
        assertEquals(2, built.filteredPets());
        assertEquals("Loro rojo", plain(built.layout().items().get(10).name()));
        assertEquals("Loro verde", plain(built.layout().items().get(11).name()));
        assertFalse(built.layout().items().get(10).lore().stream()
                .map(PetsMenuLayoutFactoryTest::plain).anyMatch(line -> line.contains("Renombrar")));
        assertEquals("Actual: Loro", plain(built.layout().items().get(47).lore().getFirst()));
    }

    @Test
    void filterWithoutMatchesShowsAnInformativeCentredItem() {
        var pets = List.of(pet(1, "dog", "Perro", "WOLF_SPAWN_EGG",
                false, false, PetStatus.ALIVE, 0, 0));

        MenuLayout layout = factory.build(data(pets), PetFilter.PARROT, 0, true).layout();

        assertEquals("ENDER_EYE", layout.items().get(22).material());
        assertEquals("Ninguna mascota de este tipo", plain(layout.items().get(22).name()));
        assertEquals(List.of("Cambia el filtro para ver otras"),
                layout.items().get(22).lore().stream().map(PetsMenuLayoutFactoryTest::plain).toList());
        assertTrue(layout.items().get(22).actionId().isEmpty());
    }

    @Test
    void writesEveryRequiredServerIndependentPreviewAsJson() throws Exception {
        Path output = Path.of(System.getProperty("neopets.preview.dir"));
        MenuLayoutJson.write(output.resolve("pets-empty.json"),
                factory.build(data(List.of()), PetFilter.ALL, 0, true).layout(), List.of(4, 22, 47));
        MenuLayoutJson.write(output.resolve("pets-reference.json"),
                factory.build(referencePets(), PetFilter.ALL, 0, true).layout(), List.of(4, 10, 13));
        PetsMenuData thirty = data(thirtyPets());
        MenuLayoutJson.write(output.resolve("pets-30-page-1.json"),
                factory.build(thirty, PetFilter.ALL, 0, true).layout(), List.of(4, 10, 50));
        MenuLayoutJson.write(output.resolve("pets-30-page-2.json"),
                factory.build(thirty, PetFilter.ALL, 1, true).layout(), List.of(4, 10, 48));
        var mixed = new ArrayList<>(referencePets().pets());
        mixed.add(pet(5, "cat", "Gato negro", "CAT_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0));
        MenuLayoutJson.write(output.resolve("pets-filter-parrot.json"),
                factory.build(data(mixed), PetFilter.PARROT, 0, true).layout(), List.of(4, 10, 47));
        MenuLayoutJson.write(output.resolve("pets-filter-empty.json"),
                factory.build(data(mixed), PetFilter.DONKEY, 0, true).layout(), List.of(4, 22, 47));
    }

    private static PetsMenuData referencePets() {
        return new PetsMenuData(List.of(
                pet(1, "parrot", "Loro azul", "PARROT_SPAWN_EGG", false, true, PetStatus.ALIVE, 4, 2),
                pet(2, "parrot", "Loro verde", "PARROT_SPAWN_EGG", false, false, PetStatus.ALIVE, 0, 0),
                pet(3, "polar_bear", "Oso polar", "POLAR_BEAR_SPAWN_EGG", true, false,
                        PetStatus.ALIVE, 0, 0),
                pet(4, "dog", "Perro negro", "WOLF_SPAWN_EGG", false, false, PetStatus.DEAD, 0, 0)),
                3, 1, 3, 10);
    }

    private static List<PetMenuEntry> thirtyPets() {
        var pets = new ArrayList<PetMenuEntry>();
        String[] types = {"dog", "cat", "parrot", "polar_bear", "donkey"};
        String[] icons = {"WOLF_SPAWN_EGG", "CAT_SPAWN_EGG", "PARROT_SPAWN_EGG",
                "POLAR_BEAR_SPAWN_EGG", "DONKEY_SPAWN_EGG"};
        for (int index = 0; index < 30; index++) {
            pets.add(pet(index + 1, types[index % types.length], "Mascota " + (index + 1),
                    icons[index % icons.length], index % 7 == 0, index == 0, PetStatus.ALIVE,
                    index % 11, index % 4));
        }
        return List.copyOf(pets);
    }

    private static PetsMenuData data(List<PetMenuEntry> pets) {
        return new PetsMenuData(pets, 3, 1, 3, 10);
    }

    private static PetMenuEntry pet(
            int number,
            String type,
            String name,
            String material,
            boolean baby,
            boolean active,
            PetStatus status,
            int nodes,
            int points
    ) {
        UUID id = UUID.nameUUIDFromBytes(("preview-pet-" + number).getBytes(StandardCharsets.UTF_8));
        return new PetMenuEntry(id.toString(), type, name, material, baby, status, active, nodes, points);
    }

    private static int contentCount(MenuLayout layout) {
        return (int) Paging.CONTENT_SLOTS.stream().filter(layout.items()::containsKey).count();
    }

    private static String plain(String miniMessage) {
        return PlainTextComponentSerializer.plainText().serialize(MINI_MESSAGE.deserialize(miniMessage));
    }
}
