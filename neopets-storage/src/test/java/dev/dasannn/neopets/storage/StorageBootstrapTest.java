package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
                            "SELECT COUNT(*) FROM np_flyway_schema_history "
                                    + "WHERE version = '1' AND success = 1")) {
                result.next();
                assertEquals(1, result.getInt(1));
            }
        } finally {
            bootstrap.close();
        }

        assertTrue(bootstrap.executor().isShutdown());
        assertTrue(((HikariDataSource) bootstrap.dataSource()).isClosed());
    }

    @Test
    void databaseExecutorRejectsWorkWhenItsBoundedQueueIsSaturated() throws Exception {
        try (var bootstrap = new StorageBootstrap(sqliteSettings())) {
            var running = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            bootstrap.executor().execute(() -> {
                running.countDown();
                awaitLatch(release);
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));

            var executor = (ThreadPoolExecutor) bootstrap.executor();
            var queueCapacity = executor.getQueue().remainingCapacity();
            for (var index = 0; index < queueCapacity; index++) {
                executor.execute(() -> {
                });
            }

            try {
                assertEquals(0, executor.getQueue().remainingCapacity());
                assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
                }));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void closeFinishesQueuedDatabaseWorkBeforeClosingThePool() throws Exception {
        var bootstrap = new StorageBootstrap(sqliteSettings());
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var queuedWorkRanWhileDataSourceWasOpen = new AtomicBoolean();
        bootstrap.executor().execute(() -> {
            running.countDown();
            awaitLatch(release);
        });
        assertTrue(running.await(5, TimeUnit.SECONDS));
        var queued = bootstrap.executor().submit(() -> queuedWorkRanWhileDataSourceWasOpen.set(
                !((HikariDataSource) bootstrap.dataSource()).isClosed()));

        var closing = CompletableFuture.runAsync(bootstrap::close);
        try {
            assertTrue(awaitShutdown(bootstrap.executor(), Duration.ofSeconds(5)));
            assertFalse(closing.isDone());
        } finally {
            release.countDown();
        }

        closing.get(5, TimeUnit.SECONDS);
        queued.get(5, TimeUnit.SECONDS);
        assertTrue(queuedWorkRanWhileDataSourceWasOpen.get());
        assertTrue(bootstrap.executor().isTerminated());
        assertTrue(((HikariDataSource) bootstrap.dataSource()).isClosed());
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean awaitShutdown(ExecutorService executor, Duration timeout) throws InterruptedException {
        var deadline = System.nanoTime() + timeout.toNanos();
        while (!executor.isShutdown() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        return executor.isShutdown();
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
