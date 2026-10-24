package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.plugin.config.LanguageCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Builds the approved six-row "Mis mascotas" layout from pure display data. */
public final class PetsMenuLayoutFactory {
    public static final String ACTION_FILTER = "pets:filter";
    public static final String ACTION_PREVIOUS_PAGE = "pets:previous-page";
    public static final String ACTION_CLOSE = "pets:close";
    public static final String ACTION_NEXT_PAGE = "pets:next-page";
    public static final String ACTION_HELP = "pets:help";
    public static final String ACTION_PET_PREFIX = "pets:pet:";

    private static final int ROWS = 6;
    private static final List<Integer> SIDE_SLOTS = List.of(9, 17, 18, 26, 27, 35, 36, 44);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final LanguageCatalog language;

    public PetsMenuLayoutFactory(LanguageCatalog language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    /** Builds one page and returns both its layout and clamped page metadata. */
    public BuiltLayout build(PetsMenuData data, PetFilter filter, int requestedPage, boolean renameEnabled) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(filter, "filter");
        List<PetMenuEntry> filtered = data.pets().stream()
                .filter(pet -> filter.includes(pet.typeId()))
                .toList();
        Paging.Page<PetMenuEntry> page = Paging.page(filtered, requestedPage);

        var items = new LinkedHashMap<Integer, MenuItem>();
        addFrame(items);
        items.put(4, header(data, page));
        for (int index = 0; index < page.values().size(); index++) {
            items.put(Paging.CONTENT_SLOTS.get(index), petItem(page.values().get(index), data, renameEnabled));
        }
        if (page.values().isEmpty()) {
            items.put(22, emptyItem(data.pets().isEmpty()));
        }
        items.put(47, filterItem(filter));
        items.put(48, previousItem(page));
        items.put(49, MenuItem.action("BARRIER", text("gui.pets.close"), List.of(), false, ACTION_CLOSE));
        items.put(50, nextItem(page));
        items.put(51, MenuItem.action("BOOK", text("gui.pets.help-name"),
                lines("gui.pets.help-lore"), false, ACTION_HELP));
        return new BuiltLayout(new MenuLayout(text("gui.pets-title"), ROWS, items), page.pageIndex(),
                page.totalPages(), filtered.size());
    }

    private void addFrame(Map<Integer, MenuItem> items) {
        String blank = text("gui.pets.blank");
        for (int slot = 0; slot <= 8; slot++) {
            String material = slot == 0 || slot == 8
                    ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE";
            items.put(slot, MenuItem.decoration(material, blank));
        }
        SIDE_SLOTS.forEach(slot -> items.put(slot, MenuItem.decoration("GRAY_STAINED_GLASS_PANE", blank)));
        for (int slot : List.of(45, 46, 52, 53)) {
            items.put(slot, MenuItem.decoration("BLACK_STAINED_GLASS_PANE", blank));
        }
    }

    private MenuItem header(PetsMenuData data, Paging.Page<?> page) {
        long active = data.pets().stream().filter(PetMenuEntry::active).count();
        return new MenuItem("NETHER_STAR", text("gui.pets.header-name"), List.of(
                text("gui.pets.header-owned", "owned", data.pets().size()),
                text("gui.pets.header-active", Map.of(
                        "active", Long.toString(active), "maximum", Integer.toString(data.maxActivePets()))),
                text("gui.pets.header-sp", "points", data.skillPointBalance()),
                pageNumber(page),
                "",
                text("gui.pets.header-hint")), false, 1, Optional.empty());
    }

    private MenuItem emptyItem(boolean hasNoPets) {
        String prefix = hasNoPets ? "gui.pets.empty" : "gui.pets.no-filter-matches";
        return new MenuItem("ENDER_EYE", text(prefix + "-name"), List.of(text(prefix + "-lore")),
                false, 1, Optional.empty());
    }

    private MenuItem petItem(PetMenuEntry pet, PetsMenuData data, boolean renameEnabled) {
        boolean dead = pet.status() == PetStatus.DEAD;
        String material = dead ? "GRAY_DYE" : pet.iconMaterial();
        String name = text(dead ? "gui.pets.pet-name-dead"
                : pet.active() ? "gui.pets.pet-name-active" : "gui.pets.pet-name", "name", pet.displayName());
        var lore = new ArrayList<String>();
        lore.add(text("gui.pets.pet-subtitle", Map.of(
                "type", text(typeLanguageKey(pet.typeId())),
                "age", text(pet.baby() ? "gui.pets.age-baby" : "gui.pets.age-adult"))));
        lore.add("");
        if (dead) {
            lore.add(text("gui.pets.state-dead"));
            lore.add(text("gui.pets.revive-line-one"));
            lore.add(text("gui.pets.revive-line-two"));
            return new MenuItem(material, name, lore, false, 1, Optional.empty());
        }

        lore.add(text(pet.active() ? "gui.pets.state-active" : "gui.pets.state-stored"));
        lore.add(text("gui.pets.progress", Map.of(
                "nodes", Integer.toString(pet.unlockedNodes()),
                "node_max", Integer.toString(data.maxNodesPerPet()),
                "points", Integer.toString(pet.assignedSkillPoints()),
                "point_max", Integer.toString(data.maxSkillPointsPerPet()))));
        lore.add("");
        lore.add(text(pet.active() ? "gui.pets.action-dismiss" : "gui.pets.action-summon"));
        lore.add(text("gui.pets.action-skills"));
        if (renameEnabled) {
            lore.add(text("gui.pets.action-rename"));
        }
        if (pet.courierSaddlebagsActive()) {
            lore.add(text("gui.pets.action-courier"));
        }
        return MenuItem.action(material, name, lore, pet.active(), ACTION_PET_PREFIX + pet.petId());
    }

    private MenuItem filterItem(PetFilter filter) {
        return MenuItem.action("HOPPER", text("gui.pets.filter-name"), List.of(
                text("gui.pets.filter-current", "filter", text(filter.languageKey())),
                "",
                text("gui.pets.filter-action")), false, ACTION_FILTER);
    }

    private MenuItem previousItem(Paging.Page<?> page) {
        if (!page.hasPrevious()) {
            return inactiveControlPane();
        }
        return MenuItem.action("ARROW", text("gui.pets.previous-page"), List.of(pageNumber(page)), false,
                ACTION_PREVIOUS_PAGE);
    }

    private MenuItem nextItem(Paging.Page<?> page) {
        if (!page.hasNext()) {
            return inactiveControlPane();
        }
        return MenuItem.action("ARROW", text("gui.pets.next-page"), List.of(pageNumber(page)), false,
                ACTION_NEXT_PAGE);
    }

    private MenuItem inactiveControlPane() {
        return MenuItem.decoration("GRAY_STAINED_GLASS_PANE", text("gui.pets.blank"));
    }

    private String pageNumber(Paging.Page<?> page) {
        return text("gui.pets.page-number", Map.of(
                "page", Integer.toString(page.displayNumber()),
                "pages", Integer.toString(page.totalPages())));
    }

    private List<String> lines(String key) {
        String source = source(key);
        return source.lines().toList();
    }

    private String text(String key) {
        return source(key);
    }

    private String text(String key, String placeholder, Object value) {
        return text(key, Map.of(placeholder, value.toString()));
    }

    private String text(String key, Map<String, String> placeholders) {
        var resolvers = TagResolver.builder();
        placeholders.forEach((name, value) -> resolvers.resolver(Placeholder.unparsed(name, value)));
        return MINI_MESSAGE.serialize(MINI_MESSAGE.deserialize(source(key), resolvers.build()));
    }

    private String source(String key) {
        return language.find(key).orElseThrow(() -> new IllegalStateException("Missing language message: " + key));
    }

    private static String typeLanguageKey(String typeId) {
        return switch (typeId) {
            case "dog" -> "gui.pets.type-dog";
            case "cat" -> "gui.pets.type-cat";
            case "parrot" -> "gui.pets.type-parrot";
            case "polar_bear" -> "gui.pets.type-polar-bear";
            case "donkey" -> "gui.pets.type-donkey";
            default -> throw new IllegalArgumentException("Unknown pet type: " + typeId);
        };
    }

    /** A rendered layout together with the page selected after clamping. */
    public record BuiltLayout(MenuLayout layout, int pageIndex, int totalPages, int filteredPets) {
        public BuiltLayout {
            Objects.requireNonNull(layout, "layout");
            if (pageIndex < 0 || totalPages < 1 || pageIndex >= totalPages || filteredPets < 0) {
                throw new IllegalArgumentException("invalid built layout metadata");
            }
        }
    }
}
