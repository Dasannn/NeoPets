package dev.dasannn.neopets.plugin.gui;

import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Normalizes chat input to a safe visible pet name. */
final class PetNameValidator {
    private static final int MAX_VISIBLE_CHARACTERS = 32;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private PetNameValidator() {
    }

    static Optional<String> normalize(String input) {
        Objects.requireNonNull(input, "input");
        if (hasLineBreak(input)) {
            return Optional.empty();
        }
        String visible;
        try {
            visible = PLAIN_TEXT.serialize(MINI_MESSAGE.deserialize(input)).strip();
        } catch (RuntimeException invalidMiniMessage) {
            return Optional.empty();
        }
        int length = visible.codePointCount(0, visible.length());
        if (length == 0 || length > MAX_VISIBLE_CHARACTERS || hasLineBreak(visible)) {
            return Optional.empty();
        }
        return Optional.of(visible);
    }

    private static boolean hasLineBreak(String value) {
        return value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
    }
}
