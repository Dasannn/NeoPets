package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.core.port.PendingSoulDeliveryRepository;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous storage for soul items awaiting delivery. */
public final class JdbcPendingSoulDeliveryRepository extends JdbcRepository
        implements PendingSoulDeliveryRepository {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String SAVE = """
            INSERT INTO np_pending_soul_delivery (token_id, pet_id, owner, created_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String FIND_BY_OWNER = """
            SELECT token_id, pet_id, owner, created_at
            FROM np_pending_soul_delivery
            WHERE owner = ?
            ORDER BY created_at, token_id
            """;
    private static final String DELETE = "DELETE FROM np_pending_soul_delivery WHERE token_id = ?";

    /** Creates a repository using the bootstrap's data source and database executor. */
    public JdbcPendingSoulDeliveryRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Void> save(PendingSoulDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        return submit(() -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(SAVE)) {
                statement.setString(1, delivery.tokenId().toString());
                statement.setString(2, delivery.petId().toString());
                statement.setString(3, delivery.ownerId().toString());
                statement.setTimestamp(4, Timestamp.from(delivery.createdAt()), utcCalendar());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<PendingSoulDelivery>> findByOwner(PlayerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        return submit(() -> {
            var deliveries = new ArrayList<PendingSoulDelivery>();
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(FIND_BY_OWNER)) {
                statement.setString(1, ownerId.toString());
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        deliveries.add(new PendingSoulDelivery(
                                UUID.fromString(result.getString("token_id")),
                                new PetId(UUID.fromString(result.getString("pet_id"))),
                                new PlayerId(UUID.fromString(result.getString("owner"))),
                                result.getTimestamp("created_at", utcCalendar()).toInstant()));
                    }
                }
            }
            return List.copyOf(deliveries);
        });
    }

    @Override
    public CompletableFuture<Void> delete(UUID tokenId) {
        Objects.requireNonNull(tokenId, "tokenId");
        return submit(() -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(DELETE)) {
                statement.setString(1, tokenId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }
}
