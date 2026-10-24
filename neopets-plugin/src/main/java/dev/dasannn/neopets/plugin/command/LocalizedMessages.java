package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;

/** Renders every player-facing string from the live language configuration. */
public final class LocalizedMessages {
    private final ConfigurationManager configuration;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LocalizedMessages(ConfigurationManager configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    /** Sends a prefixed configured message. */
    public void send(CommandSender recipient, MessageSpec message) {
        Objects.requireNonNull(recipient, "recipient");
        recipient.sendMessage(Component.text().append(render(MessageSpec.of("prefix")))
                .append(Component.space()).append(render(message)).build());
    }

    /** Renders one configured message using safely escaped placeholder values. */
    public Component render(MessageSpec message) {
        Objects.requireNonNull(message, "message");
        String source = configuration.current().language().find(message.key())
                .orElseThrow(() -> new IllegalStateException("Missing language message: " + message.key()));
        var placeholders = TagResolver.builder();
        for (Map.Entry<String, String> entry : message.placeholders().entrySet()) {
            placeholders.resolver(Placeholder.unparsed(entry.getKey(), entry.getValue()));
        }
        return miniMessage.deserialize(source, placeholders.build());
    }

    /** Renders and flattens a configured message for use as a safe placeholder value. */
    public String plain(String key) {
        return PlainTextComponentSerializer.plainText().serialize(render(MessageSpec.of(key)));
    }
}
