package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.SoulTokenRepository;
import dev.dasannn.neopets.core.pet.SoulToken;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous persistence for single-use soul tokens. */
public final class JdbcSoulTokenRepository extends JdbcRepository implements SoulTokenRepository {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String ISSUE = """
            INSERT INTO np_soul_token
                (token_id, pet_id, owner, issued_at, consumed_at, delivered_at, active_pet_id)
            VALUES (?, ?, ?, ?, NULL, NULL, ?)
            """;
    private static final String FIND_UNCONSUMED_BY_PET = """
            SELECT token_id, pet_id, owner, issued_at
            FROM np_soul_token
            WHERE active_pet_id = ? AND consumed_at IS NULL
            """;
    private static final String IS_USABLE = """
            SELECT 1 FROM np_soul_token
            WHERE token_id = ? AND pet_id = ? AND owner = ? AND consumed_at IS NULL
            """;
    private static final String CONSUME = """
            UPDATE np_soul_token SET consumed_at = ?, active_pet_id = NULL
            WHERE token_id = ? AND pet_id = ? AND owner = ? AND consumed_at IS NULL
            """;
    private static final String WAS_DELIVERED = """
            SELECT 1 FROM np_soul_token
            WHERE token_id = ? AND pet_id = ? AND owner = ? AND delivered_at IS NOT NULL
            """;
    private static final String MARK_DELIVERED = """
            UPDATE np_soul_token SET delivered_at = ?
            WHERE token_id = ? AND pet_id = ? AND owner = ? AND delivered_at IS NULL
            """;

    /** Creates a repository using the bootstrap's data source and database executor. */
    public JdbcSoulTokenRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Void> issue(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(issuedAt, "issuedAt");
        return submit(() -> {
            issueToken(tokenId, petId, ownerId, issuedAt);
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> isUsable(UUID tokenId, PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        return submit(() -> findUsable(tokenId, petId, ownerId));
    }

    @Override
    public CompletableFuture<Optional<SoulToken>> findUnconsumedByPet(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return submit(() -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(FIND_UNCONSUMED_BY_PET)) {
                statement.setString(1, petId.toString());
                try (var result = statement.executeQuery()) {
                    if (!result.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new SoulToken(
                            UUID.fromString(result.getString("token_id")),
                            new PetId(UUID.fromString(result.getString("pet_id"))),
                            new PlayerId(UUID.fromString(result.getString("owner"))),
                            result.getTimestamp("issued_at", utcCalendar()).toInstant()));
                }
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> consume(
            UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(consumedAt, "consumedAt");
        return submit(() -> consumeToken(tokenId, petId, ownerId, consumedAt));
    }

    @Override
    public CompletableFuture<Boolean> wasDelivered(UUID tokenId, PetId petId, PlayerId ownerId) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        return submit(() -> tokenMatches(WAS_DELIVERED, tokenId, petId, ownerId));
    }

    @Override
    public CompletableFuture<Boolean> markDelivered(
            UUID tokenId, PetId petId, PlayerId ownerId, Instant deliveredAt
    ) {
        Objects.requireNonNull(tokenId, "tokenId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        return submit(() -> updateTimestamp(MARK_DELIVERED, deliveredAt, tokenId, petId, ownerId));
    }

    private void issueToken(UUID tokenId, PetId petId, PlayerId ownerId, Instant issuedAt) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(ISSUE)) {
            statement.setString(1, tokenId.toString());
            statement.setString(2, petId.toString());
            statement.setString(3, ownerId.toString());
            statement.setTimestamp(4, Timestamp.from(issuedAt), utcCalendar());
            statement.setString(5, petId.toString());
            statement.executeUpdate();
        }
    }

    private boolean findUsable(UUID tokenId, PetId petId, PlayerId ownerId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(IS_USABLE)) {
            statement.setString(1, tokenId.toString());
            statement.setString(2, petId.toString());
            statement.setString(3, ownerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean consumeToken(
            UUID tokenId, PetId petId, PlayerId ownerId, Instant consumedAt) throws SQLException {
        return updateTimestamp(CONSUME, consumedAt, tokenId, petId, ownerId);
    }

    private boolean tokenMatches(
            String sql, UUID tokenId, PetId petId, PlayerId ownerId
    ) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenId.toString());
            statement.setString(2, petId.toString());
            statement.setString(3, ownerId.toString());
            try (var result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean updateTimestamp(
            String sql, Instant timestamp, UUID tokenId, PetId petId, PlayerId ownerId
    ) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(timestamp), utcCalendar());
            statement.setString(2, tokenId.toString());
            statement.setString(3, petId.toString());
            statement.setString(4, ownerId.toString());
            return statement.executeUpdate() == 1;
        }
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }
}
