package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.sql.SQLException;
import java.sql.Connection;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** JDBC-backed asynchronous persistence for complete pet skill-tree progress. */
public final class JdbcTreeProgressRepository extends JdbcRepository implements TreeProgressRepository {
    private static final String LOAD = """
            SELECT attribute_id, unlocked_level, active_level
            FROM np_pet_attribute WHERE pet_id = ?
            """;
    private static final String DELETE = "DELETE FROM np_pet_attribute WHERE pet_id = ?";
    private static final String INSERT = """
            INSERT INTO np_pet_attribute (pet_id, attribute_id, unlocked_level, active_level)
            VALUES (?, ?, ?, ?)
            """;

    private final Function<PetId, CompletableFuture<SkillTreeDefinition>> definitionResolver;

    /** Creates a repository whose resolver supplies the current definition for each pet. */
    public JdbcTreeProgressRepository(
            StorageBootstrap bootstrap,
            Function<PetId, CompletableFuture<SkillTreeDefinition>> definitionResolver
    ) {
        super(bootstrap);
        this.definitionResolver = Objects.requireNonNull(definitionResolver, "definitionResolver");
    }

    @Override
    public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return resolveDefinition(petId).thenCompose(definition -> submit(() -> load(petId, definition)));
    }

    @Override
    public CompletableFuture<Void> save(TreeProgress progress) {
        Objects.requireNonNull(progress, "progress");
        return submit(() -> {
            saveAll(progress);
            return null;
        });
    }

    @Override
    public CompletableFuture<Integer> resetForDeath(PlayerId owner, TreeProgress zeroed, int refund) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(zeroed, "zeroed");
        if (refund < 0 || zeroed.unlockedNodes() != 0 || zeroed.activeNodes() != 0) {
            throw new IllegalArgumentException("death reset requires zeroed progress and a nonnegative refund");
        }
        return submit(() -> {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    int changed;
                    try (var claim = connection.prepareStatement("""
                            UPDATE np_pet_death_work SET skills_handled = TRUE
                            WHERE pet_id = ? AND owner = ? AND keep_skills = FALSE AND skills_handled = FALSE
                            """)) {
                        claim.setString(1, zeroed.petId().toString());
                        claim.setString(2, owner.toString());
                        changed = claim.executeUpdate();
                    }
                    if (changed != 0) {
                        try (var delete = connection.prepareStatement(DELETE)) {
                            delete.setString(1, zeroed.petId().toString());
                            delete.executeUpdate();
                        }
                        String sql = storageType == StorageType.MYSQL ? """
                                INSERT INTO np_player (uuid, sp_balance) VALUES (?, ?)
                                ON DUPLICATE KEY UPDATE sp_balance = sp_balance + VALUES(sp_balance)
                                """ : """
                                INSERT INTO np_player (uuid, sp_balance) VALUES (?, ?)
                                ON CONFLICT(uuid) DO UPDATE SET sp_balance = sp_balance + excluded.sp_balance
                                """;
                        try (var credit = connection.prepareStatement(sql)) {
                            credit.setString(1, owner.toString());
                            credit.setInt(2, refund);
                            credit.executeUpdate();
                        }
                    } else {
                        try (var checkpoint = connection.prepareStatement("""
                                SELECT 1 FROM np_pet_death_work
                                WHERE pet_id = ? AND owner = ? AND keep_skills = FALSE AND skills_handled = TRUE
                                """)) {
                            checkpoint.setString(1, zeroed.petId().toString());
                            checkpoint.setString(2, owner.toString());
                            try (var result = checkpoint.executeQuery()) {
                                if (!result.next()) {
                                    throw new SQLException("Missing resettable death workflow");
                                }
                            }
                        }
                    }
                    int balance;
                    try (var select = connection.prepareStatement("SELECT sp_balance FROM np_player WHERE uuid = ?")) {
                        select.setString(1, owner.toString());
                        try (var result = select.executeQuery()) {
                            balance = result.next() ? Math.toIntExact(result.getLong(1)) : 0;
                            if (balance < 0) {
                                throw new SQLException("Skill-point balance overflow");
                            }
                        }
                    }
                    connection.commit();
                    return balance;
                } catch (Throwable failure) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        failure.addSuppressed(rollbackFailure);
                    }
                    throw failure;
                } finally {
                    connection.setAutoCommit(true);
                }
            }
        });
    }

    private Optional<TreeProgress> load(PetId petId, SkillTreeDefinition definition) throws SQLException {
        var levels = new LinkedHashMap<String, int[]>();
        try (var connection = dataSource.getConnection()) {
            remapLegacyAttributes(connection, petId, definition);
            try (var statement = connection.prepareStatement(LOAD)) {
                statement.setString(1, petId.toString());
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        levels.put(result.getString("attribute_id"), new int[] {
                                result.getInt("unlocked_level"), result.getInt("active_level")});
                    }
                }
            }
        }
        if (levels.isEmpty()) {
            return Optional.empty();
        }
        TreeProgress progress = TreeProgress.empty(petId, definition);
        for (var entry : levels.entrySet()) {
            if (!progress.attributes().containsKey(entry.getKey())) {
                throw new IllegalStateException("Stored skill-tree attribute is no longer configured: "
                        + entry.getKey());
            }
            progress = progress.withLevels(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
        return Optional.of(progress);
    }

    /** Uses the resolved pet type, never variant-name prefixes, to rename persisted progress. */
    private static void remapLegacyAttributes(
            Connection connection, PetId petId, SkillTreeDefinition definition
    ) throws SQLException {
        var configured = definition.attributes().stream()
                .map(AttributeDefinition::id)
                .collect(Collectors.toSet());
        var remaps = new LinkedHashMap<String, String>();
        try (var select = connection.prepareStatement("""
                SELECT old_attribute_id, new_attribute_id FROM np_tree_attribute_remap WHERE pet_type = ?
                """)) {
            select.setString(1, definition.petTypeId().value());
            try (var result = select.executeQuery()) {
                while (result.next()) {
                    remaps.put(result.getString(1), result.getString(2));
                }
            }
        }
        for (var remap : remaps.entrySet()) {
            if (configured.contains(remap.getKey()) || !configured.contains(remap.getValue())) {
                continue;
            }
            // A single atomic UPDATE preserves both levels and leaves balances untouched.
            // Once renamed there is no matching old row, making retries idempotent.
            try (var update = connection.prepareStatement("""
                    UPDATE np_pet_attribute SET attribute_id = ? WHERE pet_id = ? AND attribute_id = ?
                    """)) {
                update.setString(1, remap.getValue());
                update.setString(2, petId.toString());
                update.setString(3, remap.getKey());
                update.executeUpdate();
            }
        }
    }

    private void saveAll(TreeProgress progress) throws SQLException {
        try (var connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (var delete = connection.prepareStatement(DELETE)) {
                    delete.setString(1, progress.petId().toString());
                    delete.executeUpdate();
                }
                try (var insert = connection.prepareStatement(INSERT)) {
                    for (var entry : progress.attributes().entrySet()) {
                        insert.setString(1, progress.petId().toString());
                        insert.setString(2, entry.getKey());
                        insert.setInt(3, entry.getValue().unlockedLevel());
                        insert.setInt(4, entry.getValue().activeLevel());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (Throwable failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private CompletableFuture<SkillTreeDefinition> resolveDefinition(PetId petId) {
        try {
            return Objects.requireNonNull(definitionResolver.apply(petId), "definition resolver future");
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }
}
