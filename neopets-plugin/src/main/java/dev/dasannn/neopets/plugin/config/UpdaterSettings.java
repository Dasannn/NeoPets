package dev.dasannn.neopets.plugin.config;

/** Validated updater settings; diagnostic output never exposes credentials. */
public record UpdaterSettings(boolean enabled, String repository, String githubToken, boolean includePrereleases) {
    public UpdaterSettings {
        if (!validRepository(repository)) throw new IllegalArgumentException("Invalid updater repository");
        if (githubToken == null || githubToken.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid updater token [REDACTED]");
        }
    }

    /** Checks GitHub owner/repository syntax without accepting URL or path injection. */
    public static boolean validRepository(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]+/[A-Za-z0-9_.-]+")
                && !value.endsWith("/.") && !value.endsWith("/..");
    }

    /** Environment credentials take precedence, including an explicitly empty value. */
    public String token(String environmentToken) {
        return token(environmentToken, "");
    }

    /** The bundled credential is only a fallback for an empty configured token. */
    public String token(String environmentToken, String bundledToken) {
        if (environmentToken != null) return environmentToken;
        return githubToken.isBlank() ? bundledToken : githubToken;
    }

    @Override public String toString() {
        return "UpdaterSettings[enabled=" + enabled + ", repository=" + repository
                + ", githubToken=[REDACTED], includePrereleases=" + includePrereleases + "]";
    }
}
