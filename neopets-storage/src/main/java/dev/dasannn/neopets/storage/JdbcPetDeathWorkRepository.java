package dev.dasannn.neopets.storage;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.pet.PetDeathWork;
import dev.dasannn.neopets.core.port.PetDeathWorkRepository;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** JDBC-backed asynchronous checkpoints for resumable pet-death work. */
public final class JdbcPetDeathWorkRepository extends JdbcRepository implements PetDeathWorkRepository {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String UPDATE = """
            UPDATE np_pet_death_work
            SET owner = ?, keep_skills = ?, skills_handled = ?, completed = ?, created_at = ?
            WHERE pet_id = ?
            """;
    private static final String INSERT = """
            INSERT INTO np_pet_death_work
                (pet_id, owner, keep_skills, skills_handled, completed, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_PET = """
            SELECT pet_id, owner, keep_skills, skills_handled, completed, created_at
            FROM np_pet_death_work WHERE pet_id = ?
            """;
    private static final String FIND_BY_OWNER = """
            SELECT pet_id, owner, keep_skills, skills_handled, completed, created_at
            FROM np_pet_death_work WHERE owner = ? ORDER BY created_at, pet_id
            """;

    /** Creates a repository using the bootstrap's data source and database executor. */
    public JdbcPetDeathWorkRepository(StorageBootstrap bootstrap) {
        super(bootstrap);
    }

    @Override
    public CompletableFuture<Void> save(PetDeathWork work) {
        java.util.Objects.requireNonNull(work, "work");
        return submit(() -> {
            try (var connection = dataSource.getConnection();
                    var update = connection.prepareStatement(UPDATE)) {
                update.setString(1, work.ownerId().toString());
                update.setBoolean(2, work.keepSkills());
                update.setBoolean(3, work.skillsHandled());
                update.setBoolean(4, work.completed());
                update.setTimestamp(5, Timestamp.from(work.createdAt()), utcCalendar());
                update.setString(6, work.petId().toString());
                if (update.executeUpdate() == 0) {
                    try (var insert = connection.prepareStatement(INSERT)) {
                        insert.setString(1, work.petId().toString());
                        insert.setString(2, work.ownerId().toString());
                        insert.setBoolean(3, work.keepSkills());
                        insert.setBoolean(4, work.skillsHandled());
                        insert.setBoolean(5, work.completed());
                        insert.setTimestamp(6, Timestamp.from(work.createdAt()), utcCalendar());
                        insert.executeUpdate();
                    }
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<PetDeathWork>> findByPet(PetId petId) {
        java.util.Objects.requireNonNull(petId, "petId");
        return submit(() -> {
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(FIND_BY_PET)) {
                statement.setString(1, petId.toString());
                try (var result = statement.executeQuery()) {
                    return result.next() ? Optional.of(read(result)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public CompletableFuture<List<PetDeathWork>> findByOwner(PlayerId ownerId) {
        java.util.Objects.requireNonNull(ownerId, "ownerId");
        return submit(() -> {
            var work = new ArrayList<PetDeathWork>();
            try (var connection = dataSource.getConnection();
                    var statement = connection.prepareStatement(FIND_BY_OWNER)) {
                statement.setString(1, ownerId.toString());
                try (var result = statement.executeQuery()) {
                    while (result.next()) {
                        work.add(read(result));
                    }
                }
            }
            return List.copyOf(work);
        });
    }

    private static PetDeathWork read(java.sql.ResultSet result) throws java.sql.SQLException {
        return new PetDeathWork(
                new PetId(UUID.fromString(result.getString("pet_id"))),
                new PlayerId(UUID.fromString(result.getString("owner"))),
                result.getBoolean("keep_skills"),
                result.getBoolean("skills_handled"),
                result.getBoolean("completed"),
                result.getTimestamp("created_at", utcCalendar()).toInstant());
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(UTC);
    }
}
