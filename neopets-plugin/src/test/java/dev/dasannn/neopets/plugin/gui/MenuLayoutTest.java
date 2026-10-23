package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {
    @Test
    void copiesItsPureDataAndOrdersSlots() {
        var item = new MenuItem("STONE", "<white>Piedra", List.of("<gray>Lore"), false, 1,
                Optional.of("stone"));
        var layout = new MenuLayout("Título", 2, Map.of(10, item, 1, item));

        assertEquals(List.of(1, 10), layout.items().keySet().stream().toList());
        assertEquals(18, layout.size());
    }

    @Test
    void rejectsRowsSlotsAndAmountsOutsideInventoryBounds() {
        assertThrows(IllegalArgumentException.class, () -> new MenuLayout("Título", 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MenuLayout("Título", 1,
                Map.of(9, MenuItem.decoration("STONE", "Piedra"))));
        assertThrows(IllegalArgumentException.class,
                () -> new MenuItem("STONE", "Piedra", List.of(), false, 0, Optional.empty()));
    }
}
