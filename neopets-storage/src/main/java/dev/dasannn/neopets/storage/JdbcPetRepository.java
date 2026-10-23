package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.PetRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous persistence for owned pets. */
public final class JdbcPetRepository extends JdbcRepository implements PetRepository {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String COLUMNS = "id, owner, variant_id, name, status, active, created_at";
    private static final String FIND_BY_ID = "SELECT " + COLUMNS + " FROM np_pet WHERE id = ?";
    private static final String FIND_BY_OWNER = "SELECT " + COLUMNS + " FROM np_pet WHERE owner = ?";
    private static final String DELETE_BY_ID = "DELETE FROM np_pet WHERE id = ?";
    private static final String FIND_CONFLICT = """
            SELECT id FROM np_pet WHERE owner = ? AND variant_id = ? AND id <> ?
            """;
    private static final String MYSQL_SAVE = """
            INSERT INTO np_pet (id, owner, variant_id, name, status, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                id = IF(id = VALUES(id), id, NULL),
                owner = VALUES(owner),
                variant_id = VALUES(variant_id),
                name = VALUES(name),
                status = VALUES(status),
                active = VALUES(active),
                created_at = VALUES(created_at)
            """;
    private static final String SQLITE_SAVE = """
            INSERT INTO np_pet (id, owner, variant_id, name, status, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                owner = excluded.owner,
                variant_id = excluded.variant_id,
                name = excluded.name,
                status = excluded.status,
                active = excluded.active,
                created_at = excluded.created_at
            """;

    /** Creates a repository using the bootstrap's data source, dialect, and database executor. */
    public JdbcPetRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return submit(() -> find(petId));
    }

    @Override
    public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return submit(() -> findAll(ownerId));
    }

    @Override
    public CompletableFuture<Void> save(OwnedPet pet) {
        Objects.requireNonNull(pet, "pet");
        return submit(() -> {
            savePet(pet);
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> reviveWithSoul(OwnedPet revived, UUID tokenId, Instant consumedAt) {
        Objects.requireNonNull(revived, "revived");
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(consumedAt, "consumedAt");
        return submit(() -> {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (var token = connection.prepareStatement("""
                            UPDATE np_soul_token SET consumed_at = ?, active_pet_id = NULL
                            WHERE token_id = ? AND pet_id = ? AND owner = ? AND consumed_at IS NULL
                            """)) {
                        token.setTimestamp(1, Timestamp.from(consumedAt), utcCalendar());
                        token.setString(2, tokenId.toString());
                        token.setString(3, revived.id().toString());
                        token.setString(4, revived.owner().toString());
                        if (token.executeUpdate() != 1) {
                            connection.rollback();
                            return false;
                        }
                    }
                    try (var pet = connection.prepareStatement("""
                            UPDATE np_pet SET status = 'ALIVE', active = FALSE
                            WHERE id = ? AND owner = ? AND status = 'DEAD'
                            """)) {
                        pet.setString(1, revived.id().toString());
                        pet.setString(2, revived.owner().toString());
                        if (pet.executeUpdate() != 1) {
                            connection.rollback();
                            return false;
                        }
                    }
                    connection.commit();
                    return true;
                } catch (SQLException | RuntimeException failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                }
            }
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

    private Optional<OwnedPet> find(PetId petId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setString(1, petId.toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? Optional.of(readPet(result)) : Optional.empty();
            }
        }
    }

    private List<OwnedPet> findAll(PlayerId ownerId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_OWNER)) {
            statement.setString(1, ownerId.toString());
            try (var result = statement.executeQuery()) {
                var pets = new ArrayList<OwnedPet>();
                while (result.next()) {
                    pets.add(readPet(result));
                }
                return List.copyOf(pets);
            }
        }
    }

    private void savePet(OwnedPet pet) throws SQLException {
        var sql = storageType == StorageType.MYSQL ? MYSQL_SAVE : SQLITE_SAVE;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, pet.id().toString());
            statement.setString(2, pet.owner().toString());
            statement.setString(3, pet.variant().value());
            statement.setString(4, pet.customName().orElse(null));
            statement.setString(5, pet.status().name());
            statement.setBoolean(6, pet.active());
            statement.setTimestamp(7, Timestamp.from(pet.createdAt()), utcCalendar());
            try {
                statement.executeUpdate();
            } catch (SQLException exception) {
                if (hasOwnerVariantConflict(connection, pet)) {
                    throw new DuplicatePetVariantException(pet.owner(), pet.variant());
                }
                throw exception;
            }
        }
    }

    private static boolean hasOwnerVariantConflict(java.sql.Connection connection, OwnedPet pet) {
        try (var statement = connection.prepareStatement(FIND_CONFLICT)) {
            statement.setString(1, pet.owner().toString());
            statement.setString(2, pet.variant().value());
            statement.setString(3, pet.id().toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException ignored) {
            return false;
        }
    }

    private void delete(PetId petId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(DELETE_BY_ID)) {
            statement.setString(1, petId.toString());
            statement.executeUpdate();
        }
    }

    private static OwnedPet readPet(ResultSet result) throws SQLException {
        return new OwnedPet(
                new PetId(UUID.fromString(result.getString("id"))),
                new PlayerId(UUID.fromString(result.getString("owner"))),
                new VariantId(result.getString("variant_id")),
                Optional.ofNullable(result.getString("name")),
                PetStatus.valueOf(result.getString("status")),
                result.getBoolean("active"),
                readInstant(result, "created_at"));
    }

    private static Instant readInstant(ResultSet result, String column) throws SQLException {
        return result.getTimestamp(column, utcCalendar()).toInstant();
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }
}
