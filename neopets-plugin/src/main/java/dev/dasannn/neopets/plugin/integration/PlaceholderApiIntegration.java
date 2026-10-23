package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.plugin.NeoPetsPlugin;
import java.util.Objects;

/** Isolated PlaceholderAPI registration; load this class only after confirming PAPI is enabled. */
public final class PlaceholderApiIntegration {
    private PlaceholderApiIntegration() {
    }

    /** Registers the expansion and returns its unregister action. */
    public static AutoCloseable register(NeoPetsPlugin plugin, NeoPetsPlaceholders placeholders) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(placeholders, "placeholders");
        var metadata = plugin.getPluginMeta();
        String author = metadata.getAuthors().isEmpty()
                ? metadata.getName() : String.join(", ", metadata.getAuthors());
        var expansion = new NeoPetsExpansion(author, metadata.getVersion(), placeholders);
        if (!expansion.register()) {
            throw new IllegalStateException("PlaceholderAPI rejected the NeoPets expansion");
        }
        plugin.getLogger().info("PlaceholderAPI expansion registered");
        return expansion::unregister;
    }
}
