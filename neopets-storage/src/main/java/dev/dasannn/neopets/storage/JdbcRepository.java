package dev.dasannn.neopets.storage;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

abstract class JdbcRepository {
    protected final DataSource dataSource;
    protected final StorageType storageType;

    private final Executor executor;

    JdbcRepository(StorageBootstrap bootstrap) {
        Objects.requireNonNull(bootstrap, "bootstrap");
        dataSource = bootstrap.dataSource();
        executor = bootstrap.executor();
        storageType = bootstrap.storageType();
    }

    protected final <T> CompletableFuture<T> submit(SqlOperation<T> operation) {
        var future = new CompletableFuture<T>();
        try {
            executor.execute(() -> {
                try {
                    future.complete(operation.execute());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    @FunctionalInterface
    protected interface SqlOperation<T> {
        T execute() throws Exception;
    }
}
