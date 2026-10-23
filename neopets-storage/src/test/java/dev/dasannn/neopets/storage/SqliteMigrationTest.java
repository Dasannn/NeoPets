package dev.dasannn.neopets.storage;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.io.TempDir;

final class SqliteMigrationTest extends AbstractMigrationTest {
    @TempDir
    Path temporaryDirectory;

    @Override
    StorageSettings settings() {
        return new StorageSettings(
                StorageType.SQLITE,
                temporaryDirectory.resolve("neopets-" + System.nanoTime() + ".db"),
                null,
                0,
                null,
                null,
                null,
                4,
                Duration.ofSeconds(5));
    }
}
