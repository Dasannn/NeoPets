package dev.dasannn.neopets.storage;

import java.sql.DriverManager;
import java.time.Duration;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
final class MysqlJdbcRepositoryTest extends AbstractJdbcRepositoryTest {
    @Container
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("neopets")
            .withUsername("neopets")
            .withPassword("neopets");

    @Override
    void beforeBootstrap() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            AbstractMigrationTest.execute(connection, "SET FOREIGN_KEY_CHECKS = 0");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_pet_attribute");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_pet_storage");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_soul_token");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_pet");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_player");
            AbstractMigrationTest.execute(connection, "DROP TABLE IF EXISTS np_flyway_schema_history");
            AbstractMigrationTest.execute(connection, "SET FOREIGN_KEY_CHECKS = 1");
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
