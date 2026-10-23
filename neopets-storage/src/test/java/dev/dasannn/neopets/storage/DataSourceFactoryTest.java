package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DataSourceFactoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sqliteUsesOneConnectionWalAndForeignKeys() throws Exception {
        var databaseFile = temporaryDirectory.resolve("nested").resolve("neopets.db");
        var settings = new StorageSettings(
                StorageType.SQLITE,
                databaseFile,
                null,
                0,
                null,
                null,
                null,
                8,
                Duration.ofSeconds(5));

        try (var dataSource = DataSourceFactory.create(settings);
                var connection = dataSource.getConnection()) {
            assertEquals(1, dataSource.getMaximumPoolSize());
            assertEquals("wal", pragma(connection, "journal_mode"));
            assertEquals("1", pragma(connection, "foreign_keys"));
        }

        assertTrue(Files.exists(databaseFile));
    }

    private static String pragma(java.sql.Connection connection, String pragma) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("PRAGMA " + pragma)) {
            result.next();
            return result.getString(1);
        }
    }
}
