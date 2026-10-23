package dev.dasannn.neopets.storage;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

/**
 * Owns the migrated storage data source and its dedicated database executor.
 */
public final class StorageBootstrap implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);
    private static final int DATABASE_QUEUE_CAPACITY = 1_024;
    private static final String SCHEMA_HISTORY_TABLE = StorageSettings.TABLE_PREFIX + "flyway_schema_history";

    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final StorageType storageType;

    /**
     * Creates the connection pool, migrates its schema, and starts the database executor.
     *
     * @param settings storage configuration
     */
    public StorageBootstrap(StorageSettings settings) {
        Objects.requireNonNull(settings, "settings");
        storageType = settings.type();
        dataSource = DataSourceFactory.create(settings);
        try {
            migrate(dataSource);
            var threadCount = settings.type() == StorageType.SQLITE ? 1 : settings.maximumPoolSize();
            executor = new ThreadPoolExecutor(
                    threadCount,
                    threadCount,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(DATABASE_QUEUE_CAPACITY),
                    new DatabaseThreadFactory(),
                    new ThreadPoolExecutor.AbortPolicy());
        } catch (RuntimeException exception) {
            dataSource.close();
            throw exception;
        }
    }

    /**
     * Returns the migrated connection pool.
     *
     * @return the storage data source
     */
    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Returns the bounded executor reserved for database operations.
     *
     * @return the database executor
     */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * Returns the database dialect selected for this storage instance.
     *
     * @return the configured storage type
     */
    public StorageType storageType() {
        return storageType;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
                executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            dataSource.close();
        }
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table(SCHEMA_HISTORY_TABLE)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private static final class DatabaseThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            return Thread.ofPlatform()
                    .name("NeoPets-DB-" + sequence.incrementAndGet())
                    .unstarted(task);
        }
    }
}
