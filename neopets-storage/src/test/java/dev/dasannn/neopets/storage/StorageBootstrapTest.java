package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class StorageBootstrapTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesDatabaseAndOwnsNamedExecutor() throws Exception {
        var bootstrap = new StorageBootstrap(sqliteSettings());

        try {
            var threadName = bootstrap.executor().submit(() -> Thread.currentThread().getName())
                    .get(5, TimeUnit.SECONDS);
            assertEquals("NeoPets-DB-1", threadName);
            assertTrue(((ThreadPoolExecutor) bootstrap.executor()).getQueue().remainingCapacity() > 0);

            try (var connection = bootstrap.dataSource().getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        } finally {
            bootstrap.close();
        }

        assertTrue(bootstrap.executor().isShutdown());
        assertTrue(((HikariDataSource) bootstrap.dataSource()).isClosed());
    }

    private StorageSettings sqliteSettings() {
        return new StorageSettings(
                StorageType.SQLITE,
                temporaryDirectory.resolve("neopets.db"),
                null,
                0,
                null,
                null,
                null,
                4,
                Duration.ofSeconds(5));
    }
}
