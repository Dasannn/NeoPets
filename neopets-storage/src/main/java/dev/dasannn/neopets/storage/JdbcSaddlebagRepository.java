package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.courier.Saddlebag;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.SaddlebagRepository;
import dev.dasannn.neopets.core.port.SaddlebagVersionConflictException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** JDBC saddlebag persistence shared by the SQLite and MySQL storage modes. */
public final class JdbcSaddlebagRepository extends JdbcRepository implements SaddlebagRepository {
    private static final String LOAD = "SELECT owner, contents, version FROM np_pet_saddlebag WHERE pet_id = ?";
    private static final String MYSQL_INSERT = """
            INSERT INTO np_pet_saddlebag (pet_id, owner, contents, version) VALUES (?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE pet_id = pet_id
            """;
    private static final String SQLITE_INSERT = """
            INSERT INTO np_pet_saddlebag (pet_id, owner, contents, version) VALUES (?, ?, ?, 0)
            ON CONFLICT(pet_id) DO NOTHING
            """;
    private static final String UPDATE = """
            UPDATE np_pet_saddlebag SET contents = ?, version = version + 1
            WHERE pet_id = ? AND owner = ? AND version = ?
            """;

    public JdbcSaddlebagRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Saddlebag> loadOrCreate(PetId petId, PlayerId owner, byte[] emptyContents) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(owner, "owner");
        byte[] snapshot = Arrays.copyOf(Objects.requireNonNull(emptyContents, "emptyContents"), emptyContents.length);
        return submit(() -> {
            insertIfMissing(petId, owner, snapshot);
            Saddlebag bag = load(petId);
            if (!bag.owner().equals(owner)) {
                throw new SQLException("saddlebag owner mismatch for pet " + petId);
            }
            return bag;
        });
    }

    @Override
    public CompletableFuture<Saddlebag> save(PetId petId, PlayerId owner, byte[] contents, long expectedVersion) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(owner, "owner");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        byte[] snapshot = Arrays.copyOf(Objects.requireNonNull(contents, "contents"), contents.length);
        return submit(() -> {
            try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(UPDATE)) {
                statement.setBytes(1, snapshot);
                statement.setString(2, petId.toString());
                statement.setString(3, owner.toString());
                statement.setLong(4, expectedVersion);
                if (statement.executeUpdate() != 1) {
                    throw new SaddlebagVersionConflictException(petId, expectedVersion);
                }
            }
            return new Saddlebag(petId, owner, snapshot, expectedVersion + 1);
        });
    }

    private void insertIfMissing(PetId petId, PlayerId owner, byte[] contents) throws SQLException {
        String sql = storageType == StorageType.MYSQL ? MYSQL_INSERT : SQLITE_INSERT;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, petId.toString());
            statement.setString(2, owner.toString());
            statement.setBytes(3, contents);
            statement.executeUpdate();
        }
    }

    private Saddlebag load(PetId petId) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(LOAD)) {
            statement.setString(1, petId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("missing saddlebag row after insert for pet " + petId);
                return new Saddlebag(petId, new PlayerId(java.util.UUID.fromString(result.getString("owner"))),
                        result.getBytes("contents"), result.getLong("version"));
            }
        }
    }
}
