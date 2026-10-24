package dev.dasannn.neopets.plugin.config;

import java.nio.file.Path;
import java.util.Objects;

/** One actionable configuration validation failure. */
public record ConfigurationIssue(Path file, String path, String reason) {
    public ConfigurationIssue {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(reason, "reason");
        if (path.isBlank() || reason.isBlank()) {
            throw new IllegalArgumentException("path and reason must not be blank");
        }
    }

    @Override
    public String toString() {
        return file + ":" + path + ": " + reason;
    }
}
