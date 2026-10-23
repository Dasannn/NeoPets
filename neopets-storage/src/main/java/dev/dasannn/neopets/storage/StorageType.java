package dev.dasannn.neopets.storage;

/**
 * Database engines supported by NeoPets storage.
 */
public enum StorageType {
    /** A MySQL or MariaDB server. */
    MYSQL,

    /** A local SQLite database file. */
    SQLITE
}
