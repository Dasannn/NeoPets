package dev.dasannn.neopets.plugin.config;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Thrown after a load attempt has collected every discoverable configuration problem. */
public final class ConfigurationLoadException extends Exception {
    private final List<ConfigurationIssue> issues;

    public ConfigurationLoadException(List<ConfigurationIssue> issues) {
        super(formatMessage(issues));
        this.issues = List.copyOf(issues);
        if (this.issues.isEmpty()) {
            throw new IllegalArgumentException("issues must not be empty");
        }
    }

    /** Returns all failures found in the load attempt, in file traversal order. */
    public List<ConfigurationIssue> issues() {
        return issues;
    }

    private static String formatMessage(List<ConfigurationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        return "Invalid NeoPets configuration (" + issues.size() + " issue(s)):\n"
                + issues.stream().map(issue -> " - " + issue).collect(Collectors.joining("\n"));
    }
}
