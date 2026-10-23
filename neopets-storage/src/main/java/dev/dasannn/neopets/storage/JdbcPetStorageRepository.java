package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.PetStorageRepository;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous persistence for opaque pet inventory contents. */
public final class JdbcPetStorageRepository extends JdbcRepository implements PetStorageRepository {
    private static final String LOAD = "SELECT contents FROM np_pet_storage WHERE pet_id = ?";
    private static final String DELETE_BY_ID = "DELETE FROM np_pet_storage WHERE pet_id = ?";
    private static final String MYSQL_SAVE = """
            INSERT INTO np_pet_storage (pet_id, contents) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE contents = VALUES(contents)
            """;
    private static final String SQLITE_SAVE = """
            INSERT INTO np_pet_storage (pet_id, contents) VALUES (?, ?)
            ON CONFLICT(pet_id) DO UPDATE SET contents = excluded.contents
            """;

    /** Creates a repository using the bootstrap's data source, dialect, and database executor. */
    public JdbcPetStorageRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Optional<byte[]>> load(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return submit(() -> loadContents(petId));
    }

    @Override
    public CompletableFuture<Void> save(PetId petId, byte[] contents) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(contents, "contents");
        var snapshot = Arrays.copyOf(contents, contents.length);
        return submit(() -> {
            saveContents(petId, snapshot);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteById(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return submit(() -> {
            delete(petId);
            return null;
        });
    }

    private Optional<byte[]> loadContents(PetId petId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LOAD)) {
            statement.setString(1, petId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                var contents = result.getBytes("contents");
                return Optional.of(Arrays.copyOf(contents, contents.length));
            }
        }
    }

    private void saveContents(PetId petId, byte[] contents) throws SQLException {
        var sql = storageType == StorageType.MYSQL ? MYSQL_SAVE : SQLITE_SAVE;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, petId.toString());
            statement.setBytes(2, contents);
            statement.executeUpdate();
        }
    }

    private void delete(PetId petId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(DELETE_BY_ID)) {
            statement.setString(1, petId.toString());
            statement.executeUpdate();
        }
    }
}
