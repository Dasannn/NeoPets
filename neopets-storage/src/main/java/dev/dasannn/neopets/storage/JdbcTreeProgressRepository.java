package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.sql.SQLException;
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

    private Optional<TreeProgress> load(PetId petId, SkillTreeDefinition definition) throws SQLException {
        var levels = new LinkedHashMap<String, int[]>();
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(LOAD)) {
            statement.setString(1, petId.toString());
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    levels.put(result.getString("attribute_id"), new int[] {
                            result.getInt("unlocked_level"), result.getInt("active_level")});
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
