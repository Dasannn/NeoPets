package dev.dasannn.neopets.plugin.update;

/** Typed updater outcomes with credential-free failure codes. */
public sealed interface UpdateResult {
    /** Release information returned by a successful check. */
    record Checked(String installed, String latest, String name, String notes, boolean newer) implements UpdateResult { }
    /** A verified update is ready for restart. */
    record Staged(String version) implements UpdateResult { }
    /** Expected errors never retain server bodies, exception messages or tokens. */
    enum Failure implements UpdateResult {
        DISABLED, NO_TOKEN, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_LIMITED,
        NO_NEWER_VERSION, MISSING_ASSET, CHECKSUM_MISMATCH, BAD_SIGNATURE,
        PLACEHOLDER_KEY, IO_ERROR, BUSY;

        public String key() { return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'); }
    }
}
