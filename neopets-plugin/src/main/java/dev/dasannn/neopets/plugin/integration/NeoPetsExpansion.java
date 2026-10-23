package dev.dasannn.neopets.plugin.integration;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import java.util.Objects;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** PlaceholderAPI expansion for the {@code %neopets_*%} namespace. */
public final class NeoPetsExpansion extends PlaceholderExpansion {
    private final String author;
    private final String version;
    private final NeoPetsPlaceholders placeholders;

    /** Creates the expansion with plugin metadata and a snapshot-only resolver. */
    public NeoPetsExpansion(String author, String version, NeoPetsPlaceholders placeholders) {
        this.author = Objects.requireNonNull(author, "author");
        this.version = Objects.requireNonNull(version, "version");
        this.placeholders = Objects.requireNonNull(placeholders, "placeholders");
    }

    @Override
    public @NotNull String getIdentifier() {
        return "neopets";
    }

    @Override
    public @NotNull String getAuthor() {
        return author;
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return onRequest(player, params);
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return placeholders.resolve(player == null ? null : player.getUniqueId(), params);
    }
}
