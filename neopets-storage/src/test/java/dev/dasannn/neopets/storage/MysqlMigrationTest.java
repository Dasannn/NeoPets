package dev.dasannn.neopets.storage;

import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
final class MysqlMigrationTest extends AbstractMigrationTest {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("neopets")
            .withUsername("neopets")
            .withPassword("neopets");

    @BeforeEach
    void clearDatabase() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            execute(connection, "SET FOREIGN_KEY_CHECKS = 0");
            execute(connection, "DROP TABLE IF EXISTS np_pet_attribute");
            execute(connection, "DROP TABLE IF EXISTS np_pet_storage");
            execute(connection, "DROP TABLE IF EXISTS np_soul_token");
            execute(connection, "DROP TABLE IF EXISTS np_pending_soul_delivery");
            execute(connection, "DROP TABLE IF EXISTS np_pet_death_work");
            execute(connection, "DROP TABLE IF EXISTS np_pet");
            execute(connection, "DROP TABLE IF EXISTS np_player");
            execute(connection, "DROP TABLE IF EXISTS np_flyway_schema_history");
            execute(connection, "DROP TABLE IF EXISTS unrelated_plugin_data");
            execute(connection, "SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Override
    StorageSettings settings() {
        return new StorageSettings(
                StorageType.MYSQL,
                null,
                MYSQL.getHost(),
                MYSQL.getMappedPort(MySQLContainer.MYSQL_PORT),
                MYSQL.getDatabaseName(),
                MYSQL.getUsername(),
                MYSQL.getPassword(),
                4,
                Duration.ofSeconds(10));
    }
}
