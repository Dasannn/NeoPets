package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqliteJdbcRepositoryTest extends AbstractJdbcRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Override
    StorageSettings settings() {
        return new StorageSettings(
                StorageType.SQLITE,
                temporaryDirectory.resolve("repositories.db"),
                null,
                0,
                null,
                null,
                null,
                4,
                Duration.ofSeconds(5));
    }

    @Test
    void failedPetUpdateRollsBackTokenConsumptionAndAllowsRetry() throws Exception {
        try (var storage = new StorageBootstrap(settings())) {
            var pets = new JdbcPetRepository(storage);
            var tokens = new JdbcSoulTokenRepository(storage);
            var owner = new PlayerId(UUID.randomUUID());
            var id = new PetId(UUID.randomUUID());
            var dead = new OwnedPet(id, owner,
                    new VariantId("dog_brown"), Optional.empty(),
                    PetStatus.DEAD, false, Instant.parse("2026-10-23T12:00:00Z"));
            pets.save(dead).join();
            var token = UUID.randomUUID();
            tokens.issue(token, id, owner, Instant.now()).join();
            var alive = dead.withStatus(PetStatus.ALIVE);
            try (var connection = storage.dataSource().getConnection(); var sql = connection.createStatement()) {
                sql.execute("""
                        CREATE TRIGGER fail_revive BEFORE UPDATE OF status ON np_pet
                        BEGIN SELECT RAISE(ABORT, 'injected pet write failure'); END
                        """);
            }
            var failure = assertThrows(CompletionException.class,
                    () -> pets.reviveWithSoul(alive, token, Instant.now()).join());
            assertTrue(failure.getCause().getMessage().contains("injected pet write failure"));
            assertTrue(tokens.isUsable(token, id, owner).join());
            assertEquals(dead, pets.findById(id).join().orElseThrow());
            try (var connection = storage.dataSource().getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP TRIGGER fail_revive");
            }
            assertTrue(pets.reviveWithSoul(alive, token, Instant.now()).join());
            assertFalse(tokens.isUsable(token, id, owner).join());
            assertEquals(alive, pets.findById(id).join().orElseThrow());
            assertFalse(pets.reviveWithSoul(alive, token, Instant.now()).join());
        }
    }
}
