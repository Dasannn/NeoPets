package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PlayerRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous persistence for player skill-point balances. */
public final class JdbcPlayerRepository extends JdbcRepository implements PlayerRepository {
    private static final String FIND_BY_ID = "SELECT uuid, sp_balance FROM np_player WHERE uuid = ?";
    private static final String MYSQL_SAVE = """
            INSERT INTO np_player (uuid, sp_balance) VALUES (?, ?)
            ON DUPLICATE KEY UPDATE sp_balance = VALUES(sp_balance)
            """;
    private static final String SQLITE_SAVE = """
            INSERT INTO np_player (uuid, sp_balance) VALUES (?, ?)
            ON CONFLICT(uuid) DO UPDATE SET sp_balance = excluded.sp_balance
            """;

    /** Creates a repository using the bootstrap's data source, dialect, and database executor. */
    public JdbcPlayerRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return submit(() -> find(playerId));
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return submit(() -> {
            saveProfile(profile);
            return null;
        });
    }

    private Optional<PlayerProfile> find(PlayerId playerId) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                var storedId = new PlayerId(UUID.fromString(result.getString("uuid")));
                return Optional.of(new PlayerProfile(storedId, result.getInt("sp_balance"), List.of()));
            }
        }
    }

    private void saveProfile(PlayerProfile profile) throws SQLException {
        var sql = storageType == StorageType.MYSQL ? MYSQL_SAVE : SQLITE_SAVE;
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, profile.playerId().toString());
            statement.setInt(2, profile.skillPointBalance());
            statement.executeUpdate();
        }
    }
}
