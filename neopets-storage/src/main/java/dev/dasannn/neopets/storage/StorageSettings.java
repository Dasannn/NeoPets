package dev.dasannn.neopets.storage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Validated settings used to configure the storage connection pool.
 *
 * @param type database engine
 * @param sqliteFile SQLite database file; required for {@link StorageType#SQLITE}
 * @param mysqlHost MySQL host; required for {@link StorageType#MYSQL}
 * @param mysqlPort MySQL port
 * @param mysqlDatabase MySQL database name; required for {@link StorageType#MYSQL}
 * @param mysqlUser MySQL user; required for {@link StorageType#MYSQL}
 * @param mysqlPassword MySQL password; required for {@link StorageType#MYSQL}
 * @param maximumPoolSize maximum number of database connections
 * @param connectionTimeout maximum time to wait for a connection
 */
public record StorageSettings(
        StorageType type,
        Path sqliteFile,
        String mysqlHost,
        int mysqlPort,
        String mysqlDatabase,
        String mysqlUser,
        String mysqlPassword,
        int maximumPoolSize,
        Duration connectionTimeout) {
    /** Prefix reserved for all NeoPets tables. */
    public static final String TABLE_PREFIX = "np_";

    private static final Duration MINIMUM_CONNECTION_TIMEOUT = Duration.ofMillis(250);

    /**
     * Validates and normalizes the supplied settings.
     */
    public StorageSettings {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(connectionTimeout, "connectionTimeout");
        if (maximumPoolSize < 1) {
            throw new IllegalArgumentException("maximumPoolSize must be at least 1");
        }
        if (connectionTimeout.compareTo(MINIMUM_CONNECTION_TIMEOUT) < 0) {
            throw new IllegalArgumentException("connectionTimeout must be at least 250 ms");
        }
        try {
            connectionTimeout.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("connectionTimeout is too large", exception);
        }

        switch (type) {
            case SQLITE -> {
                sqliteFile = Objects.requireNonNull(sqliteFile, "sqliteFile")
                        .toAbsolutePath()
                        .normalize();
            }
            case MYSQL -> {
                mysqlHost = requireNonBlank(mysqlHost, "mysqlHost");
                if (mysqlPort < 1 || mysqlPort > 65_535) {
                    throw new IllegalArgumentException("mysqlPort must be between 1 and 65535");
                }
                mysqlDatabase = requireNonBlank(mysqlDatabase, "mysqlDatabase");
                mysqlUser = requireNonBlank(mysqlUser, "mysqlUser");
                Objects.requireNonNull(mysqlPassword, "mysqlPassword");
            }
        }
    }

    /**
     * Returns the fixed prefix used by the schema.
     *
     * @return {@value #TABLE_PREFIX}
     */
    public String tablePrefix() {
        return TABLE_PREFIX;
    }

    /**
     * Describes these settings without exposing the database password.
     *
     * @return a redacted settings description
     */
    @Override
    public String toString() {
        return ("StorageSettings[type=%s, sqliteFile=%s, mysqlHost=%s, mysqlPort=%d, "
                        + "mysqlDatabase=%s, mysqlUser=%s, mysqlPassword=<redacted>, "
                        + "maximumPoolSize=%d, connectionTimeout=%s]")
                .formatted(
                        type,
                        sqliteFile,
                        mysqlHost,
                        mysqlPort,
                        mysqlDatabase,
                        mysqlUser,
                        maximumPoolSize,
                        connectionTimeout);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
