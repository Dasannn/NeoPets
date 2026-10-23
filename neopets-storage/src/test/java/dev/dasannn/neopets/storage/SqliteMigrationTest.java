package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteMigrationTest {
    private static final Map<String, Set<String>> EXPECTED_COLUMNS = Map.of(
            "np_player", Set.of("uuid", "sp_balance"),
            "np_pet", Set.of("id", "owner", "variant_id", "name", "status", "active", "created_at"),
            "np_pet_attribute", Set.of("pet_id", "attribute_id", "unlocked_level", "active_level"),
            "np_soul_token", Set.of("token_id", "pet_id", "owner", "issued_at", "consumed_at"),
            "np_pet_storage", Set.of("pet_id", "contents"));

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsEveryTableColumnKeyAndOwnerIndex() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            var metadata = connection.getMetaData();

            for (var expectedTable : EXPECTED_COLUMNS.entrySet()) {
                assertTrue(tableExists(metadata, expectedTable.getKey()), expectedTable.getKey());
                assertEquals(expectedTable.getValue(), columns(metadata, expectedTable.getKey()));
            }

            assertEquals(Set.of(), nullableColumns(metadata, "np_player"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet_attribute"));
            assertEquals(Set.of("consumed_at"), nullableColumns(metadata, "np_soul_token"));
            assertEquals(Set.of(), nullableColumns(metadata, "np_pet_storage"));

            assertEquals(Set.of("uuid"), primaryKey(metadata, "np_player"));
            assertEquals(Set.of("id"), primaryKey(metadata, "np_pet"));
            assertEquals(Set.of("pet_id", "attribute_id"), primaryKey(metadata, "np_pet_attribute"));
            assertEquals(Set.of("token_id"), primaryKey(metadata, "np_soul_token"));
            assertEquals(Set.of("pet_id"), primaryKey(metadata, "np_pet_storage"));
            assertTrue(indexedColumns(metadata, "np_pet").contains(Set.of("owner")));
            assertTrue(indexedColumns(metadata, "np_soul_token").contains(Set.of("owner")));
        }
    }

    @Test
    void ownerAndVariantMustBeUniqueTogether() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            insertPet(connection, "pet-one", "owner", "dog_brown");

            assertThrows(SQLException.class, () -> insertPet(connection, "pet-two", "owner", "dog_brown"));
            insertPet(connection, "pet-three", "owner", "dog_black");
            insertPet(connection, "pet-four", "other-owner", "dog_brown");
        }
    }

    @Test
    void deletingPetCascadesToAttributesAndStorage() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            insertPet(connection, "pet-one", "owner", "donkey_brown");
            execute(connection, """
                    INSERT INTO np_pet_attribute (pet_id, attribute_id, unlocked_level, active_level)
                    VALUES ('pet-one', 'armor', 2, 1)
                    """);
            execute(connection, """
                    INSERT INTO np_pet_storage (pet_id, contents)
                    VALUES ('pet-one', X'0102')
                    """);

            execute(connection, "DELETE FROM np_pet WHERE id = 'pet-one'");

            assertEquals(0, count(connection, "np_pet_attribute"));
            assertEquals(0, count(connection, "np_pet_storage"));
        }
    }

    @Test
    void childRowsRequireAnExistingPet() throws Exception {
        try (var bootstrap = bootstrap(); var connection = bootstrap.dataSource().getConnection()) {
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO np_pet_attribute (pet_id, attribute_id, unlocked_level, active_level)
                    VALUES ('missing', 'health', 1, 1)
                    """));
            assertThrows(SQLException.class, () -> execute(connection, """
                    INSERT INTO np_pet_storage (pet_id, contents)
                    VALUES ('missing', X'00')
                    """));
        }
    }

    private StorageBootstrap bootstrap() {
        return new StorageBootstrap(new StorageSettings(
                StorageType.SQLITE,
                temporaryDirectory.resolve("neopets-" + System.nanoTime() + ".db"),
                null,
                0,
                null,
                null,
                null,
                4,
                Duration.ofSeconds(5)));
    }

    private static boolean tableExists(DatabaseMetaData metadata, String table) throws SQLException {
        try (var result = metadata.getTables(null, null, table, new String[] {"TABLE"})) {
            return result.next();
        }
    }

    private static Set<String> columns(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getColumns(null, null, table, null)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private static Set<String> primaryKey(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getPrimaryKeys(null, null, table)) {
            while (result.next()) {
                columns.add(result.getString("COLUMN_NAME").toLowerCase());
            }
        }
        return columns;
    }

    private static Set<String> nullableColumns(DatabaseMetaData metadata, String table) throws SQLException {
        var columns = new HashSet<String>();
        try (var result = metadata.getColumns(null, null, table, null)) {
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
        try (var result = metadata.getIndexInfo(null, null, table, false, false)) {
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

    private static void insertPet(Connection connection, String id, String owner, String variant) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO np_pet (id, owner, variant_id, name, status, active, created_at)
                VALUES (?, ?, ?, 'Companion', 'ALIVE', FALSE, '2026-10-23 12:00:00.000')
                """)) {
            statement.setString(1, id);
            statement.setString(2, owner);
            statement.setString(3, variant);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }
}
