package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StorageSettingsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sqliteSettingsRequireAFile() {
        assertThrows(NullPointerException.class, () -> sqliteSettings(null, 1, Duration.ofSeconds(5)));
    }

    @Test
    void commonPoolSettingsAreRequired() {
        assertThrows(NullPointerException.class, () -> new StorageSettings(
                null,
                temporaryDirectory.resolve("database.db"),
                null,
                0,
                null,
                null,
                null,
                1,
                Duration.ofSeconds(5)));
        assertThrows(NullPointerException.class, () -> sqliteSettings(
                temporaryDirectory.resolve("database.db"), 1, null));
    }

    @Test
    void mysqlSettingsRequireConnectionFields() {
        assertThrows(IllegalArgumentException.class, () -> mysqlSettings(" ", 3306, "neopets", "user", "secret"));
        assertThrows(IllegalArgumentException.class, () -> mysqlSettings("localhost", 3306, " ", "user", "secret"));
        assertThrows(IllegalArgumentException.class, () -> mysqlSettings("localhost", 3306, "neopets", " ", "secret"));
        assertThrows(NullPointerException.class, () -> mysqlSettings("localhost", 3306, "neopets", "user", null));
    }

    @Test
    void mysqlPortMustBeInTcpRange() {
        assertThrows(IllegalArgumentException.class, () -> mysqlSettings("localhost", 0, "neopets", "user", "secret"));
        assertThrows(IllegalArgumentException.class, () -> mysqlSettings("localhost", 65_536, "neopets", "user", "secret"));
    }

    @Test
    void settingsDescriptionRedactsMysqlPassword() {
        var settings = mysqlSettings("localhost", 3306, "neopets", "user", "super-secret");

        assertFalse(settings.toString().contains("super-secret"));
    }

    @Test
    void poolSizeAndTimeoutMustBeUsableByHikari() {
        assertThrows(IllegalArgumentException.class, () -> sqliteSettings(
                temporaryDirectory.resolve("database.db"), 0, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> sqliteSettings(
                temporaryDirectory.resolve("database.db"), 1, Duration.ofMillis(249)));
        assertThrows(IllegalArgumentException.class, () -> sqliteSettings(
                temporaryDirectory.resolve("database.db"), 1, Duration.ofSeconds(Long.MAX_VALUE)));
    }

    @Test
    void sqlitePathIsNormalizedAndTablePrefixIsFixed() {
        var settings = sqliteSettings(
                temporaryDirectory.resolve("nested").resolve("..").resolve("database.db"),
                4,
                Duration.ofSeconds(5));

        assertEquals(temporaryDirectory.resolve("database.db").toAbsolutePath(), settings.sqliteFile());
        assertEquals("np_", settings.tablePrefix());
    }

    private static StorageSettings sqliteSettings(Path file, int poolSize, Duration timeout) {
        return new StorageSettings(
                StorageType.SQLITE, file, null, 0, null, null, null, poolSize, timeout);
    }

    private static StorageSettings mysqlSettings(
            String host, int port, String database, String user, String password) {
        return new StorageSettings(
                StorageType.MYSQL,
                null,
                host,
                port,
                database,
                user,
                password,
                4,
                Duration.ofSeconds(5));
    }
}
