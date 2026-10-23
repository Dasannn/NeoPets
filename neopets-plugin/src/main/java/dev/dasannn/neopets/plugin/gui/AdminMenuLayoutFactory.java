package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.plugin.config.FeatureRegistry;
import dev.dasannn.neopets.plugin.config.LanguageCatalog;
import dev.dasannn.neopets.plugin.config.PluginSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Builds a guide-compliant page of editable administration features. */
public final class AdminMenuLayoutFactory {
    public static final String ACTION_PREVIOUS_PAGE = "admin:previous-page";
    public static final String ACTION_CLOSE = "admin:close";
    public static final String ACTION_NEXT_PAGE = "admin:next-page";
    public static final String ACTION_HELP = "admin:help";
    public static final String ACTION_FEATURE_PREFIX = "admin:feature:";

    private static final int ROWS = 6;
    private static final List<Integer> SIDE_SLOTS = List.of(9, 17, 18, 26, 27, 35, 36, 44);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final LanguageCatalog language;

    public AdminMenuLayoutFactory(LanguageCatalog language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    /** Builds one page from the settings visible to the viewer. */
    public BuiltLayout build(PluginSettings settings, List<FeatureRegistry.Feature> features, int requestedPage) {
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(features, "features");
        Paging.Page<FeatureRegistry.Feature> page = Paging.page(features, requestedPage);
        var items = new LinkedHashMap<Integer, MenuItem>();
        addFrame(items);
        items.put(4, header(features.size(), page));
        for (int index = 0; index < page.values().size(); index++) {
            FeatureRegistry.Feature feature = page.values().get(index);
            items.put(Paging.CONTENT_SLOTS.get(index), featureItem(feature, feature.currentValue(settings)));
        }
        items.put(47, inactiveControlPane());
        items.put(48, previousItem(page));
        items.put(49, MenuItem.action("BARRIER", text("gui.admin.close"), List.of(), false, ACTION_CLOSE));
        items.put(50, nextItem(page));
        items.put(51, MenuItem.action("BOOK", text("gui.admin.help-name"),
                lines("gui.admin.help-lore"), false, ACTION_HELP));
        return new BuiltLayout(new MenuLayout(text("gui.admin-title"), ROWS, items),
                page.pageIndex(), page.totalPages());
    }

    private void addFrame(Map<Integer, MenuItem> items) {
        String blank = text("gui.pets.blank");
        for (int slot = 0; slot <= 8; slot++) {
            items.put(slot, MenuItem.decoration(slot == 0 || slot == 8
                    ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE", blank));
        }
        SIDE_SLOTS.forEach(slot -> items.put(slot,
                MenuItem.decoration("GRAY_STAINED_GLASS_PANE", blank)));
        for (int slot : List.of(45, 46, 52, 53)) {
            items.put(slot, MenuItem.decoration(slot == 45 || slot == 53
                    ? "BLACK_STAINED_GLASS_PANE" : "GRAY_STAINED_GLASS_PANE", blank));
        }
    }

    private MenuItem header(int featureCount, Paging.Page<?> page) {
        return new MenuItem("COMMAND_BLOCK", text("gui.admin.header-name"), List.of(
                text("gui.admin.header-count", "count", featureCount),
                pageNumber(page)), false, 1, java.util.Optional.empty());
    }

    private MenuItem featureItem(FeatureRegistry.Feature feature, Object value) {
        var lore = new java.util.ArrayList<String>();
        lore.add(text(feature.descriptionKey()));
        lore.add("");
        if (feature.type() instanceof FeatureRegistry.BooleanToggle) {
            lore.add(text(Boolean.TRUE.equals(value)
                    ? "gui.admin.current-enabled" : "gui.admin.current-disabled"));
            lore.add("");
            lore.add(text("gui.admin.toggle-action"));
        } else if (feature.type() instanceof FeatureRegistry.BoundedNumber number && value instanceof Number current) {
            lore.add(text("gui.admin.current-number", "value", number.format(current)));
            lore.add(text("gui.admin.range", Map.of(
                    "minimum", (number.minimumInclusive() ? "" : "> ") + number.format(number.minimum()),
                    "maximum", number.format(number.maximum()))));
            lore.add("");
            lore.add(text("gui.admin.number-action", "step", number.format(number.step())));
        } else {
            throw new IllegalArgumentException("Value does not match feature type: " + feature.path());
        }
        return MenuItem.action(feature.iconMaterial(), text(feature.nameKey()), lore, false,
                ACTION_FEATURE_PREFIX + feature.path());
    }

    private MenuItem previousItem(Paging.Page<?> page) {
        return page.hasPrevious()
                ? MenuItem.action("ARROW", text("gui.admin.previous-page"), List.of(pageNumber(page)), false,
                        ACTION_PREVIOUS_PAGE)
                : inactiveControlPane();
    }

    private MenuItem nextItem(Paging.Page<?> page) {
        return page.hasNext()
                ? MenuItem.action("ARROW", text("gui.admin.next-page"), List.of(pageNumber(page)), false,
                        ACTION_NEXT_PAGE)
                : inactiveControlPane();
    }

    private MenuItem inactiveControlPane() {
        return MenuItem.decoration("GRAY_STAINED_GLASS_PANE", text("gui.pets.blank"));
    }

    private String pageNumber(Paging.Page<?> page) {
        return text("gui.admin.page-number", Map.of(
                "page", Integer.toString(page.displayNumber()),
                "pages", Integer.toString(page.totalPages())));
    }

    private List<String> lines(String key) {
        return source(key).lines().toList();
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

    /** Rendered layout plus its clamped page metadata. */
    public record BuiltLayout(MenuLayout layout, int pageIndex, int totalPages) {
        public BuiltLayout {
            Objects.requireNonNull(layout, "layout");
            if (pageIndex < 0 || totalPages < 1 || pageIndex >= totalPages) {
                throw new IllegalArgumentException("invalid built layout metadata");
            }
        }
    }
}
