package dev.dasannn.neopets.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Creates connection pools for the supported storage engines.
 */
public final class DataSourceFactory {
    private static final String POOL_NAME = "NeoPets-Storage";

    private DataSourceFactory() {}

    /**
     * Creates a connection pool configured for the selected database engine.
     *
     * @param settings validated storage settings
     * @return a new connection pool owned by the caller
     */
    public static HikariDataSource create(StorageSettings settings) {
        var config = baseConfig(settings);
        switch (settings.type()) {
            case MYSQL -> configureMysql(config, settings);
            case SQLITE -> configureSqlite(config, settings);
        }
        return new HikariDataSource(config);
    }

    private static HikariConfig baseConfig(StorageSettings settings) {
        var config = new HikariConfig();
        config.setPoolName(POOL_NAME);
        config.setConnectionTimeout(settings.connectionTimeout().toMillis());
        config.setMaximumPoolSize(settings.maximumPoolSize());
        return config;
    }

    private static void configureMysql(HikariConfig config, StorageSettings settings) {
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://%s:%d/%s".formatted(
                settings.mysqlHost(), settings.mysqlPort(), settings.mysqlDatabase()));
        config.setUsername(settings.mysqlUser());
        config.setPassword(settings.mysqlPassword());
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    }

    private static void configureSqlite(HikariConfig config, StorageSettings settings) {
        createParentDirectory(settings.sqliteFile());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + settings.sqliteFile());
        config.setMaximumPoolSize(1);
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
    }

    private static void createParentDirectory(Path sqliteFile) {
        var parent = sqliteFile.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create SQLite database directory: " + parent, exception);
        }
    }
}
