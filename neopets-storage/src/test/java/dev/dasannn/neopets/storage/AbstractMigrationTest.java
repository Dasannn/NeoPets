package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

abstract class AbstractMigrationTest {
    private static final Map<String, Set<String>> EXPECTED_COLUMNS = Map.of(
            "np_player", Set.of("uuid", "sp_balance"),
            "np_pet", Set.of("id", "owner", "variant_id", "name", "status", "active", "created_at"),
            "np_pet_attribute", Set.of("pet_id", "attribute_id", "unlocked_level", "active_level"),
            "np_soul_token", Set.of("token_id", "pet_id", "owner", "issued_at", "consumed_at"),
            "np_pet_storage", Set.of("pet_id", "contents"),
            "np_pending_soul_delivery", Set.of("token_id", "pet_id", "owner", "created_at"),
            "np_pet_saddlebag", Set.of("pet_id", "owner", "contents", "version"));

    abstract StorageSettings settings();

    @Test
    final void createsEveryTableColumnKeyAndOwnerIndex() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            var metadata = connection.getMetaData();

            for (var expectedTable : EXPECTED_COLUMNS.entrySet()) {
                assertTrue(tableExists(metadata, expectedTable.getKey()), expectedTable.getKey());
                assertEquals(expectedTable.getValue(), columns(metadata, expectedTable.getKey()));
            }

            assertEquals(Set.of(), nullableColumns(metadata, "np_player"));
            assertEquals(Set.of("name"), nullableColumns(metadata, "np_pet"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet_attribute"));
            assertEquals(Set.of("consumed_at"), nullableColumns(metadata, "np_soul_token"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet_storage"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pending_soul_delivery"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet_saddlebag"));

            assertEquals(Set.of("uuid"), primaryKey(metadata, "np_player"));
            assertEquals(Set.of("id"), primaryKey(metadata, "np_pet"));
            assertEquals(Set.of("pet_id", "attribute_id"), primaryKey(metadata, "np_pet_attribute"));
            assertEquals(Set.of("token_id"), primaryKey(metadata, "np_soul_token"));
            assertEquals(Set.of("pet_id"), primaryKey(metadata, "np_pet_storage"));
            assertEquals(Set.of("token_id"), primaryKey(metadata, "np_pending_soul_delivery"));
            assertEquals(Set.of("pet_id"), primaryKey(metadata, "np_pet_saddlebag"));
            assertTrue(indexedColumns(metadata, "np_pet").contains(Set.of("owner")));
            assertTrue(indexedColumns(metadata, "np_soul_token").contains(Set.of("owner")));
            assertTrue(indexedColumns(metadata, "np_pending_soul_delivery").contains(Set.of("owner")));
            assertTrue(indexedColumns(metadata, "np_pet_saddlebag").contains(Set.of("owner")));
            assertTrue(tableExists(metadata, "np_flyway_schema_history"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '1' AND success = 1"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '2' AND success = 1"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '3' AND success = 1"));
        }
    }

    @Test
    final void ownerAndVariantMustBeUniqueTogether() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            insertPet(connection, "pet-one", "owner", "dog_brown", "Companion");

            assertThrows(
                    SQLException.class,
                    () -> insertPet(connection, "pet-two", "owner", "dog_brown", "Other"));
            insertPet(connection, "pet-three", "owner", "dog_black", "Third");
            insertPet(connection, "pet-four", "other-owner", "dog_brown", "Fourth");
        }
    }

    @Test
    final void deletingPetCascadesToAttributesAndStorage() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            insertPet(connection, "pet-one", "owner", "donkey_brown", "Companion");
            execute(connection, """
                    INSERT INTO np_pet_attribute (pet_id, attribute_id, unlocked_level, active_level)
                    VALUES ('pet-one', 'armor', 2, 1)
                    """);
            insertStorage(connection, "pet-one");
            insertSaddlebag(connection, "pet-one", "owner");

            execute(connection, "DELETE FROM np_pet WHERE id = 'pet-one'");

            assertEquals(0, count(connection, "np_pet_attribute"));
            assertEquals(0, count(connection, "np_pet_storage"));
            assertEquals(0, count(connection, "np_pet_saddlebag"));
        }
    }

    @Test
    final void childRowsRequireAnExistingPet() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO np_pet_attribute (pet_id, attribute_id, unlocked_level, active_level)
                    VALUES ('missing', 'health', 1, 1)
                    """));
            assertThrows(SQLException.class, () -> insertStorage(connection, "missing"));
            assertThrows(SQLException.class, () -> insertSaddlebag(connection, "missing", "owner"));
        }
    }

    @Test
    final void persistsPetWithoutACustomName() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            insertPet(connection, "pet-one", "owner", "cat_black", null);

            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT name FROM np_pet WHERE id = 'pet-one'")) {
                assertTrue(result.next());
                assertNull(result.getString("name"));
            }
        }
    }

    @Test
    final void migratesInsideANonEmptySharedSchema() throws Exception {
        var settings = settings();
        try (var dataSource = DataSourceFactory.create(settings);
                var connection = dataSource.getConnection()) {
            execute(connection, "CREATE TABLE unrelated_plugin_data (id INTEGER NOT NULL PRIMARY KEY)");
        }

        try (var bootstrap = new StorageBootstrap(settings);
                var connection = bootstrap.dataSource().getConnection()) {
            var metadata = connection.getMetaData();
            assertTrue(tableExists(metadata, "unrelated_plugin_data"));
            assertTrue(tableExists(metadata, "np_player"));
            assertTrue(tableExists(metadata, "np_flyway_schema_history"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '0' AND success = 1"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '1' AND success = 1"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '2' AND success = 1"));
            assertEquals(1, countWhere(connection, "np_flyway_schema_history", "version = '3' AND success = 1"));
        }
    }

    @Test
    final void aSecondBootstrapPreservesExistingData() throws Exception {
        var settings = settings();
        var playerId = "10000000-0000-0000-0000-000000000001";
        try (var firstBootstrap = new StorageBootstrap(settings);
                var connection = firstBootstrap.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO np_player (uuid, sp_balance) VALUES (?, 17)")) {
            statement.setString(1, playerId);
            statement.executeUpdate();
        }

        try (var secondBootstrap = new StorageBootstrap(settings);
                var connection = secondBootstrap.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "SELECT sp_balance FROM np_player WHERE uuid = ?")) {
            statement.setString(1, playerId);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(17, result.getInt("sp_balance"));
            }
            assertEquals(1, countWhere(
                    connection, "np_flyway_schema_history", "version = '3' AND success = 1"));
        }
    }

    private StorageBootstrap bootstrap() {
        return new StorageBootstrap(settings());
    }

    private static boolean tableExists(DatabaseMetaData metadata, String table) throws SQLException {
        try (var result = metadata.getTables(catalog(metadata), null, table, new String[] {"TABLE"})) {
            return result.next();
        }
    }

    private static Set<String> columns(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getColumns(catalog(metadata), null, table, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private static Set<String> primaryKey(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getPrimaryKeys(catalog(metadata), null, table)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private static Set<String> nullableColumns(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getColumns(catalog(metadata), null, table, null)) {
            while (result.next()) {
                if (result.getInt("NULLABLE") == DatabaseMetaData.columnNullable) {
                    columns.add(result.getString("COLUMN_NAME").toLowerCase());
                }
            }
        }
        return columns;
    }

    private static Set<Set<String>> indexedColumns(DatabaseMetaData metadata, String table) throws SQLException {
        var indexes = new HashMap<String, Set<String>>();
        try (var result = metadata.getIndexInfo(catalog(metadata), null, table, false, false)) {
            while (result.next()) {
                var indexName = result.getString("INDEX_NAME");
                var columnName = result.getString("COLUMN_NAME");
                if (indexName != null && columnName != null) {
                    indexes.computeIfAbsent(indexName, ignored -> new HashSet<>())
                            .add(columnName.toLowerCase());
                }
            }
        }
        return Set.copyOf(indexes.values());
    }

    private static String catalog(DatabaseMetaData metadata) throws SQLException {
        return metadata.getConnection().getCatalog();
    }

    private static void insertPet(
            Connection connection, String id, String owner, String variant, String name) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO np_pet (id, owner, variant_id, name, status, active, created_at)
                VALUES (?, ?, ?, ?, 'ALIVE', FALSE, ?)
                """)) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, variant);
            statement.setString(4, name);
            statement.setTimestamp(5, Timestamp.valueOf("2026-10-23 12:00:00"));
            statement.executeUpdate();
        }
    }

    private static void insertStorage(Connection connection, String petId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO np_pet_storage (pet_id, contents)
                VALUES (?, ?)
                """)) {
            statement.setString(1, petId);
            statement.setBytes(2, new byte[] {1, 2});
            statement.executeUpdate();
        }
    }

    private static void insertSaddlebag(Connection connection, String petId, String owner) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO np_pet_saddlebag (pet_id, owner, contents, version)
                VALUES (?, ?, ?, 0)
                """)) {
            statement.setString(1, petId);
            statement.setString(2, owner);
            statement.setBytes(3, new byte[] {1, 2});
            statement.executeUpdate();
        }
    }

    static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        return countWhere(connection, table, "TRUE");
    }

    private static int countWhere(Connection connection, String table, String condition) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + condition)) {
            result.next();
            return result.getInt(1);
        }
    }
}
