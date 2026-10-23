package dev.dasannn.neopets.plugin.gui;

import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.AttributeLevelProgress;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import dev.dasannn.neopets.plugin.config.LanguageCatalog;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/** Builds the standard six-row, four-attribute-per-page skill-tree layout. */
public final class SkillTreeMenuLayoutFactory {
    public static final String ACTION_PREVIOUS_PAGE = "skills:previous-page";
    public static final String ACTION_NEXT_PAGE = "skills:next-page";
    public static final String ACTION_BACK = "skills:back";
    public static final String ACTION_HELP = "skills:help";
    public static final String ACTION_RESET = "skills:reset";
    public static final String ACTION_UNLOCK_PREFIX = "skills:unlock:";
    public static final String ACTION_ACTIVATE_PREFIX = "skills:activate:";

    private static final int ROWS = 6;
    private static final int ATTRIBUTES_PER_PAGE = 4;
    private static final int[] ROW_STARTS = {10, 19, 28, 37};
    private static final List<Integer> SIDE_SLOTS = List.of(9, 17, 18, 26, 27, 35, 36, 44);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<String, String> ATTRIBUTE_MATERIALS = Map.ofEntries(
            Map.entry("health", "GLISTERING_MELON_SLICE"),
            Map.entry("regeneration", "GOLDEN_APPLE"),
            Map.entry("fire_resistance", "MAGMA_CREAM"),
            Map.entry("strength", "IRON_SWORD"),
            Map.entry("speed", "SUGAR"),
            Map.entry("scent", "COMPASS"),
            Map.entry("agility", "RABBIT_FOOT"),
            Map.entry("armor", "IRON_CHESTPLATE"),
            Map.entry("flight_speed", "FEATHER"),
            Map.entry("alert_range", "SPYGLASS"),
            Map.entry("knockback_resistance", "ANVIL"),
            Map.entry("claw_swipe", "NETHER_STAR"),
            Map.entry("treasure_hunter", "EMERALD"),
            Map.entry("feline_landing", "LEATHER_BOOTS"),
            Map.entry("lookout", "BELL"),
            Map.entry("courier_saddlebags", "CHEST"));

    private final LanguageCatalog language;

    public SkillTreeMenuLayoutFactory(LanguageCatalog language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    /** Builds one clamped page, including confirmation state for the reset control. */
    public BuiltLayout build(
            SkillTreeMenuData data, int requestedPage, boolean resetConfirmation
    ) {
        Objects.requireNonNull(data, "data");
        if (requestedPage < 0) {
            throw new IllegalArgumentException("requestedPage must not be negative");
        }
        List<AttributeDefinition> attributes = data.definition().attributes();
        int totalPages = Math.max(1, (attributes.size() + ATTRIBUTES_PER_PAGE - 1) / ATTRIBUTES_PER_PAGE);
        int pageIndex = Math.min(requestedPage, totalPages - 1);
        int from = pageIndex * ATTRIBUTES_PER_PAGE;
        int to = Math.min(from + ATTRIBUTES_PER_PAGE, attributes.size());

        var items = new LinkedHashMap<Integer, MenuItem>();
        addFrame(items);
        items.put(4, header(data, pageIndex, totalPages));
        for (int row = 0; row < ATTRIBUTES_PER_PAGE; row++) {
            int start = ROW_STARTS[row];
            if (from + row < to) {
                addAttributeRow(items, start, attributes.get(from + row), data);
            }
        }
        items.put(47, resetItem(data, resetConfirmation));
        items.put(48, pageIndex > 0 ? MenuItem.action("ARROW", text("gui.skill-tree.previous-page"),
                List.of(pageNumber(pageIndex, totalPages)), false, ACTION_PREVIOUS_PAGE) : inactiveControl());
        items.put(49, MenuItem.action("OAK_DOOR", text("gui.skill-tree.back"), List.of(), false, ACTION_BACK));
        items.put(50, pageIndex + 1 < totalPages ? MenuItem.action("ARROW", text("gui.skill-tree.next-page"),
                List.of(pageNumber(pageIndex, totalPages)), false, ACTION_NEXT_PAGE) : inactiveControl());
        items.put(51, MenuItem.action("BOOK", text("gui.skill-tree.help-name"),
                lines("gui.skill-tree.help-lore"), false, ACTION_HELP));
        return new BuiltLayout(new MenuLayout(text("gui.skill-tree.title", "pet", data.petName()), ROWS, items),
                pageIndex, totalPages);
    }

    /** Computes the visual state and permitted action for one level. */
    public static SkillNodeView nodeView(
            SkillTreeMenuData data, AttributeDefinition attribute, int level
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(attribute, "attribute");
        if (level < 1 || level > attribute.maxLevel()) {
            throw new IllegalArgumentException("level is outside the attribute");
        }
        AttributeLevelProgress progress = data.progress().attributes().get(attribute.id());
        if (progress == null) {
            throw new IllegalArgumentException("progress is missing attribute " + attribute.id());
        }
        if (level <= progress.activeLevel()) {
            return view(SkillNodeState.ACTIVE, SkillNodeReason.NONE, null);
        }
        if (level <= progress.unlockedLevel()) {
            if (level > progress.activeLevel() + 1) {
                return view(SkillNodeState.UNLOCKED, SkillNodeReason.PREVIOUS_ACTIVATION, null);
            }
            int cost = attribute.skillPointCosts().get(level - 1);
            if ((long) data.assignedSkillPoints() + cost > data.maxSkillPoints()) {
                return view(SkillNodeState.UNAVAILABLE, SkillNodeReason.SKILL_POINT_LIMIT, null);
            }
            if (data.availableSkillPoints() < cost) {
                return view(SkillNodeState.UNAVAILABLE, SkillNodeReason.INSUFFICIENT_SKILL_POINTS, null);
            }
            return view(SkillNodeState.UNLOCKED, SkillNodeReason.NONE,
                    ACTION_ACTIVATE_PREFIX + attribute.id());
        }
        if (data.unlockedNodes() >= data.maxNodes()) {
            return view(SkillNodeState.UNAVAILABLE, SkillNodeReason.NODE_LIMIT, null);
        }
        if (level == progress.unlockedLevel() + 1) {
            BigDecimal cost = attribute.unlockMoneyCosts().get(level - 1);
            if (cost.signum() > 0) {
                if (data.economyBalance().isEmpty()) {
                    return view(SkillNodeState.UNAVAILABLE, SkillNodeReason.ECONOMY_UNAVAILABLE, null);
                }
                if (data.economyBalance().orElseThrow().compareTo(cost) < 0) {
                    return view(SkillNodeState.UNAVAILABLE, SkillNodeReason.INSUFFICIENT_FUNDS, null);
                }
            }
            return view(SkillNodeState.NEXT_UNLOCKABLE, SkillNodeReason.NONE,
                    ACTION_UNLOCK_PREFIX + attribute.id());
        }
        return view(SkillNodeState.LOCKED, SkillNodeReason.PREVIOUS_LEVEL, null);
    }

    private void addAttributeRow(
            Map<Integer, MenuItem> items, int start, AttributeDefinition attribute, SkillTreeMenuData data
    ) {
        if (attribute.maxLevel() > 6) {
            throw new IllegalArgumentException("skill-tree menu supports at most six levels per attribute");
        }
        items.put(start, attributeItem(attribute));
        for (int level = 1; level <= attribute.maxLevel(); level++) {
            items.put(start + level, nodeItem(data, attribute, level));
        }
    }

    private MenuItem attributeItem(AttributeDefinition attribute) {
        String material = Optional.ofNullable(ATTRIBUTE_MATERIALS.get(attribute.id()))
                .orElse(attribute.category() == AttributeCategory.CUSTOM ? "NETHER_STAR" : "BOOK");
        return new MenuItem(material, text(attributeKey(attribute, "name")), List.of(
                text(attribute.category() == AttributeCategory.VANILLA
                        ? "gui.skill-tree.category-vanilla" : "gui.skill-tree.category-custom"),
                "",
                text(attributeKey(attribute, "description"))), false, 1, Optional.empty());
    }

    private MenuItem nodeItem(SkillTreeMenuData data, AttributeDefinition attribute, int level) {
        SkillNodeView view = nodeView(data, attribute, level);
        String effect = effectLine(attribute, level);
        String levelText = Integer.toString(level);
        return switch (view.state()) {
            case ACTIVE -> item("LIME_DYE", "gui.skill-tree.node-active-name",
                    List.of(effect), levelText, true, view.actionId());
            case UNLOCKED -> item("YELLOW_DYE", "gui.skill-tree.node-unlocked-name",
                    unlockedLore(view, data, attribute, level, effect), levelText, false, view.actionId());
            case NEXT_UNLOCKABLE -> item("ORANGE_DYE", "gui.skill-tree.node-next-name",
                    List.of(effect, text("gui.skill-tree.node-money-cost", "cost",
                            money(attribute.unlockMoneyCosts().get(level - 1))), "",
                            text("gui.skill-tree.action-unlock")), levelText, false, view.actionId());
            case LOCKED -> item("GRAY_DYE", "gui.skill-tree.node-locked-name",
                    List.of(effect, "", reason(view.reason(), data)), levelText, false, view.actionId());
            case UNAVAILABLE -> item("RED_DYE", "gui.skill-tree.node-unavailable-name",
                    List.of(effect, "", reason(view.reason(), data)), levelText, false, view.actionId());
        };
    }

    private List<String> unlockedLore(
            SkillNodeView view,
            SkillTreeMenuData data,
            AttributeDefinition attribute,
            int level,
            String effect
    ) {
        if (view.reason() == SkillNodeReason.PREVIOUS_ACTIVATION) {
            return List.of(effect, "", reason(view.reason(), data));
        }
        return List.of(effect,
                text("gui.skill-tree.node-sp-cost", "cost", attribute.skillPointCosts().get(level - 1)),
                "", text("gui.skill-tree.action-activate"));
    }

    private MenuItem item(
            String material, String nameKey, List<String> lore, String level, boolean glint, Optional<String> action
    ) {
        return new MenuItem(material, text(nameKey, "level", level), lore, glint, 1, action);
    }

    private String effectLine(AttributeDefinition attribute, int level) {
        String key = attributeKey(attribute, "effect");
        if (attribute.effect() instanceof VanillaEffectReference effect) {
            BigDecimal value = effect.valuesPerLevel().get(level - 1);
            return text(key, "value", relativeValue(effect.catalogEffectId(), value));
        }
        if (attribute.effect() instanceof CustomEffectReference) {
            return text(key);
        }
        throw new IllegalStateException("Unknown effect reference: " + attribute.effect());
    }

    private String reason(SkillNodeReason reason, SkillTreeMenuData data) {
        return switch (reason) {
            case PREVIOUS_LEVEL -> text("gui.skill-tree.reason-previous");
            case PREVIOUS_ACTIVATION -> text("gui.skill-tree.reason-previous-activation");
            case NODE_LIMIT -> text("gui.skill-tree.reason-node-limit", "limit", data.maxNodes());
            case SKILL_POINT_LIMIT -> text("gui.skill-tree.reason-sp-limit", "limit", data.maxSkillPoints());
            case INSUFFICIENT_SKILL_POINTS -> text("gui.skill-tree.reason-no-sp");
            case INSUFFICIENT_FUNDS -> text("gui.skill-tree.reason-no-money");
            case ECONOMY_UNAVAILABLE -> text("gui.skill-tree.reason-no-economy");
            case NONE -> throw new IllegalArgumentException("a reason line requires a reason");
        };
    }

    private MenuItem header(SkillTreeMenuData data, int page, int pages) {
        return new MenuItem(data.iconMaterial(), text("gui.skill-tree.header-name", "pet", data.petName()), List.of(
                text("gui.skill-tree.header-subtitle", Map.of(
                        "type", text("gui.pets.type-" + data.typeId().replace('_', '-')),
                        "age", text(data.baby() ? "gui.pets.age-baby" : "gui.pets.age-adult"))),
                "",
                text("gui.skill-tree.header-available-sp", "points", data.availableSkillPoints()),
                text("gui.skill-tree.header-assigned-sp", Map.of(
                        "assigned", Integer.toString(data.assignedSkillPoints()),
                        "maximum", Integer.toString(data.maxSkillPoints()))),
                text("gui.skill-tree.header-nodes", Map.of(
                        "nodes", Integer.toString(data.unlockedNodes()),
                        "maximum", Integer.toString(data.maxNodes()))),
                pageNumber(page, pages),
                "",
                nextUnlockHint(data)), false, 1, Optional.empty());
    }

    private String nextUnlockHint(SkillTreeMenuData data) {
        if (data.unlockedNodes() >= data.maxNodes()) {
            return text("gui.skill-tree.next-limit");
        }
        for (AttributeDefinition attribute : data.definition().attributes()) {
            AttributeLevelProgress progress = data.progress().attributes().get(attribute.id());
            if (progress.unlockedLevel() < attribute.maxLevel()) {
                int next = progress.unlockedLevel() + 1;
                return text("gui.skill-tree.next-hint", Map.of(
                        "attribute", plain(text(attributeKey(attribute, "name"))),
                        "level", Integer.toString(next),
                        "cost", money(attribute.unlockMoneyCosts().get(next - 1))));
            }
        }
        return text("gui.skill-tree.next-complete");
    }

    private MenuItem resetItem(SkillTreeMenuData data, boolean confirmation) {
        if (!data.resetEnabled()) {
            return inactiveControl();
        }
        if (confirmation) {
            return MenuItem.action("TNT", text("gui.skill-tree.reset-confirm-name"), List.of(
                    text("gui.skill-tree.reset-cost", "cost", money(data.resetCost())), "",
                    text("gui.skill-tree.reset-confirm-action")), false, ACTION_RESET);
        }
        return MenuItem.action("WATER_BUCKET", text("gui.skill-tree.reset-name"), List.of(
                text("gui.skill-tree.reset-cost", "cost", money(data.resetCost())),
                text("gui.skill-tree.reset-refund"), "", text("gui.skill-tree.reset-action")),
                false, ACTION_RESET);
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
            items.put(slot, MenuItem.decoration("BLACK_STAINED_GLASS_PANE", blank));
        }
    }

    private MenuItem inactiveControl() {
        return MenuItem.decoration("GRAY_STAINED_GLASS_PANE", text("gui.pets.blank"));
    }

    private String pageNumber(int page, int pages) {
        return text("gui.skill-tree.page-number", Map.of(
                "page", Integer.toString(page + 1), "pages", Integer.toString(pages)));
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

    private static String attributeKey(AttributeDefinition attribute, String suffix) {
        return "gui.skill-tree.attributes." + attribute.id() + "." + suffix;
    }

    private static String money(BigDecimal value) {
        return "$" + number(value);
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String relativeValue(String effectId, BigDecimal value) {
        return switch (effectId) {
            case "movement_speed", "flying_speed", "scale", "knockback_resistance",
                    "explosion_knockback_resistance", "fall_damage_multiplier", "fall_damage_reduction" ->
                    number(value.multiply(BigDecimal.valueOf(100)));
            default -> number(value);
        };
    }

    private static String plain(String miniMessage) {
        return miniMessage.replaceAll("<[^>]+>", "");
    }

    private static SkillNodeView view(SkillNodeState state, SkillNodeReason reason, String action) {
        return new SkillNodeView(state, reason, Optional.ofNullable(action));
    }

    /** A rendered layout together with its selected page metadata. */
    public record BuiltLayout(MenuLayout layout, int pageIndex, int totalPages) {
        public BuiltLayout {
            Objects.requireNonNull(layout, "layout");
            if (pageIndex < 0 || totalPages < 1 || pageIndex >= totalPages) {
                throw new IllegalArgumentException("invalid built layout metadata");
            }
        }
    }
}
