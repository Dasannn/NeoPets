package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.plugin.config.LanguageCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

/** Builds courier storage and online-player picker layouts using the shared GUI design language. */
public final class CourierMenuLayoutFactory {
    public static final String ACTION_SEND = "courier:send";
    public static final String ACTION_RETURN = "courier:return";
    public static final String ACTION_BACK = "courier:back";
    public static final String ACTION_CLOSE = "courier:close";
    public static final String ACTION_HELP = "courier:help";
    public static final String ACTION_PREVIOUS = "courier:previous";
    public static final String ACTION_NEXT = "courier:next";
    public static final String ACTION_PLAYER_PREFIX = "courier:player:";
    public static final List<Integer> STORAGE_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42);
    private static final List<Integer> SIDES = List.of(9, 17, 18, 26, 27, 35, 36, 44);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final LanguageCatalog language;

    public CourierMenuLayoutFactory(LanguageCatalog language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    /** Builds the owner's 27-slot chest with the send action. */
    public MenuLayout owner(String petName, List<MenuItem> contents) {
        return chest(petName, contents, false);
    }

    /** Builds the recipient's 27-slot chest with the return action. */
    public MenuLayout recipient(String petName, List<MenuItem> contents) {
        return chest(petName, contents, true);
    }

    /** Builds one page of online player heads, excluding entries before paging at the caller. */
    public BuiltPicker picker(List<PlayerEntry> onlinePlayers, int requestedPage) {
        Paging.Page<PlayerEntry> page = Paging.page(onlinePlayers, requestedPage);
        var items = new LinkedHashMap<Integer, MenuItem>();
        addFrame(items);
        items.put(4, new MenuItem("DONKEY_SPAWN_EGG", text("gui.courier.picker-header"), List.of(
                text("gui.courier.picker-count", "count", onlinePlayers.size()), pageNumber(page)),
                false, 1, Optional.empty()));
        for (int index = 0; index < page.values().size(); index++) {
            PlayerEntry player = page.values().get(index);
            items.put(Paging.CONTENT_SLOTS.get(index), MenuItem.action("PLAYER_HEAD",
                    text("gui.courier.player-name", "player", player.name()),
                    List.of("", text("gui.courier.player-action")), false,
                    ACTION_PLAYER_PREFIX + player.id()));
        }
        if (page.values().isEmpty()) {
            items.put(22, MenuItem.decoration("BARRIER", text("gui.courier.no-players")));
        }
        items.put(48, page.hasPrevious() ? MenuItem.action("ARROW", text("gui.courier.previous"),
                List.of(pageNumber(page)), false, ACTION_PREVIOUS) : inactive());
        items.put(49, MenuItem.action("OAK_DOOR", text("gui.courier.back"), List.of(), false, ACTION_BACK));
        items.put(50, page.hasNext() ? MenuItem.action("ARROW", text("gui.courier.next"),
                List.of(pageNumber(page)), false, ACTION_NEXT) : inactive());
        items.put(51, MenuItem.action("BOOK", text("gui.courier.picker-help"),
                List.of(text("gui.courier.picker-help-lore")), false, ACTION_HELP));
        return new BuiltPicker(new MenuLayout(text("gui.courier.picker-title"), 6, items),
                page.pageIndex(), page.totalPages());
    }

    private MenuLayout chest(String petName, List<MenuItem> contents, boolean recipient) {
        Objects.requireNonNull(petName, "petName");
        Objects.requireNonNull(contents, "contents");
        if (contents.size() != STORAGE_SLOTS.size()) throw new IllegalArgumentException("contents must have 27 slots");
        var items = new LinkedHashMap<Integer, MenuItem>();
        addFrame(items);
        items.put(4, new MenuItem("CHEST", text("gui.courier.header", "pet", petName), List.of(
                text(recipient ? "gui.courier.recipient-subtitle" : "gui.courier.owner-subtitle")),
                false, 1, Optional.empty()));
        for (int index = 0; index < contents.size(); index++) {
            MenuItem item = contents.get(index);
            if (item != null) items.put(STORAGE_SLOTS.get(index), item);
        }
        items.put(47, MenuItem.action(recipient ? "LEAD" : "ENDER_PEARL",
                text(recipient ? "gui.courier.return-name" : "gui.courier.send-name"),
                List.of(text(recipient ? "gui.courier.return-action" : "gui.courier.send-action")), false,
                recipient ? ACTION_RETURN : ACTION_SEND));
        items.put(49, MenuItem.action(recipient ? "BARRIER" : "OAK_DOOR",
                text(recipient ? "gui.courier.close" : "gui.courier.back"), List.of(), false,
                recipient ? ACTION_CLOSE : ACTION_BACK));
        items.put(51, MenuItem.action("BOOK", text("gui.courier.storage-help"),
                List.of(text("gui.courier.storage-help-lore")), false, ACTION_HELP));
        return new MenuLayout(text("gui.courier.title"), 6, items);
    }

    private void addFrame(Map<Integer, MenuItem> items) {
        String blank = text("gui.courier.blank");
        for (int slot = 0; slot <= 8; slot++) {
            items.put(slot, MenuItem.decoration(slot == 0 || slot == 8
                    ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE", blank));
        }
        SIDES.forEach(slot -> items.put(slot, MenuItem.decoration("GRAY_STAINED_GLASS_PANE", blank)));
        for (int slot = 45; slot < 54; slot++) {
            items.put(slot, MenuItem.decoration(slot == 45 || slot == 53
                    ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE", blank));
        }
    }

    private MenuItem inactive() {
        return MenuItem.decoration("GRAY_STAINED_GLASS_PANE", text("gui.courier.blank"));
    }

    private String pageNumber(Paging.Page<?> page) {
        return MINI_MESSAGE.serialize(MINI_MESSAGE.deserialize(source("gui.courier.page"),
                Placeholder.unparsed("page", Integer.toString(page.displayNumber())),
                Placeholder.unparsed("pages", Integer.toString(page.totalPages()))));
    }

    private String text(String key) { return source(key); }

    private String text(String key, String placeholder, Object value) {
        return MINI_MESSAGE.serialize(MINI_MESSAGE.deserialize(source(key),
                Placeholder.unparsed(placeholder, value.toString())));
    }

    private String source(String key) {
        return language.find(key).orElseThrow(() -> new IllegalStateException("Missing language message: " + key));
    }

    /** Minimal online-player display data. */
    public record PlayerEntry(java.util.UUID id, String name) {
        public PlayerEntry { Objects.requireNonNull(id, "id"); Objects.requireNonNull(name, "name"); }
    }

    /** Picker layout plus clamped paging metadata. */
    public record BuiltPicker(MenuLayout layout, int pageIndex, int totalPages) { }
}
