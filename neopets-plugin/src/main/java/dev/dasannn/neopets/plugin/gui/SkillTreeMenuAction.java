package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import java.util.Optional;

/** Parsed action emitted by a skill-tree menu item. */
public sealed interface SkillTreeMenuAction {
    record PreviousPage() implements SkillTreeMenuAction { }

    record NextPage() implements SkillTreeMenuAction { }

    record Back() implements SkillTreeMenuAction { }

    record Help() implements SkillTreeMenuAction { }

    record Reset() implements SkillTreeMenuAction { }

    record Unlock(String attributeId) implements SkillTreeMenuAction {
        public Unlock {
            requireAttribute(attributeId);
        }
    }

    record Activate(String attributeId) implements SkillTreeMenuAction {
        public Activate {
            requireAttribute(attributeId);
        }
    }

    /** Parses a layout action id, rejecting ids not owned by this menu. */
    static Optional<SkillTreeMenuAction> parse(String actionId) {
        Objects.requireNonNull(actionId, "actionId");
        return switch (actionId) {
            case SkillTreeMenuLayoutFactory.ACTION_PREVIOUS_PAGE -> Optional.of(new PreviousPage());
            case SkillTreeMenuLayoutFactory.ACTION_NEXT_PAGE -> Optional.of(new NextPage());
            case SkillTreeMenuLayoutFactory.ACTION_BACK -> Optional.of(new Back());
            case SkillTreeMenuLayoutFactory.ACTION_HELP -> Optional.of(new Help());
            case SkillTreeMenuLayoutFactory.ACTION_RESET -> Optional.of(new Reset());
            default -> {
                if (actionId.startsWith(SkillTreeMenuLayoutFactory.ACTION_UNLOCK_PREFIX)) {
                    yield parsedAttribute(actionId, SkillTreeMenuLayoutFactory.ACTION_UNLOCK_PREFIX, true);
                }
                if (actionId.startsWith(SkillTreeMenuLayoutFactory.ACTION_ACTIVATE_PREFIX)) {
                    yield parsedAttribute(actionId, SkillTreeMenuLayoutFactory.ACTION_ACTIVATE_PREFIX, false);
                }
                yield Optional.empty();
            }
        };
    }

    private static Optional<SkillTreeMenuAction> parsedAttribute(
            String actionId, String prefix, boolean unlock
    ) {
        String attribute = actionId.substring(prefix.length());
        if (attribute.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(unlock ? new Unlock(attribute) : new Activate(attribute));
    }

    private static void requireAttribute(String attributeId) {
        Objects.requireNonNull(attributeId, "attributeId");
        if (attributeId.isBlank()) {
            throw new IllegalArgumentException("attributeId must not be blank");
        }
    }
}
