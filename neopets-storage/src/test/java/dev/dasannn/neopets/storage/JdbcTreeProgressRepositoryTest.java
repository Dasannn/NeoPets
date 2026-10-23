package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcTreeProgressRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndReplacesACompleteTreeUsingTheCurrentDefinition() throws Exception {
        var settings = new StorageSettings(StorageType.SQLITE, temporaryDirectory.resolve("trees.db"),
                null, 0, null, null, null, 2, Duration.ofSeconds(5));
        try (var bootstrap = new StorageBootstrap(settings)) {
            var petId = new PetId(UUID.fromString("20000000-0000-0000-0000-000000000019"));
            var owner = new PlayerId(UUID.fromString("10000000-0000-0000-0000-000000000019"));
            var pets = new JdbcPetRepository(bootstrap);
            await(pets.save(new OwnedPet(petId, owner, new VariantId("dog_pale"), Optional.empty(),
                    PetStatus.ALIVE, false, Instant.EPOCH)));
            var definition = new SkillTreeDefinition(new PetTypeId("dog"), List.of(
                    new AttributeDefinition("scent", AttributeCategory.CUSTOM, 3,
                            new CustomEffectReference("scent"),
                            List.of(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.TEN))));
            var trees = new JdbcTreeProgressRepository(
                    bootstrap, ignored -> CompletableFuture.completedFuture(definition));

            assertEquals(Optional.empty(), await(trees.load(petId)));
            var changed = TreeProgress.empty(petId, definition).withLevels("scent", 2, 1);
            await(trees.save(changed));
            assertEquals(Optional.of(changed), await(trees.load(petId)));

            var replacement = changed.withLevels("scent", 3, 2);
            await(trees.save(replacement));
            assertEquals(Optional.of(replacement), await(trees.load(petId)));
        }
    }

    @Test
    void deathResetAndRefundAreDurableAndRepeatableAcrossRestart() throws Exception {
        var settings = new StorageSettings(StorageType.SQLITE, temporaryDirectory.resolve("death.db"),
                null, 0, null, null, null, 2, Duration.ofSeconds(5));
        var petId = new PetId(UUID.randomUUID());
        var owner = new PlayerId(UUID.randomUUID());
        var definition = new SkillTreeDefinition(new PetTypeId("dog"), List.of(
                new AttributeDefinition("scent", AttributeCategory.CUSTOM, 2,
                        new CustomEffectReference("scent"), List.of(BigDecimal.ONE, BigDecimal.TEN),
                        List.of(2, 3))));
        var progress = TreeProgress.empty(petId, definition).withLevels("scent", 2, 2);
        try (var bootstrap = new StorageBootstrap(settings)) {
            await(new JdbcPetRepository(bootstrap).save(new OwnedPet(petId, owner, new VariantId("dog_pale"),
                    Optional.empty(), PetStatus.DEAD, false, Instant.EPOCH)));
            await(new JdbcPlayerRepository(bootstrap).save(
                    new dev.dasannn.neopets.core.model.PlayerProfile(owner, 7, List.of())));
            var trees = new JdbcTreeProgressRepository(bootstrap,
                    ignored -> CompletableFuture.completedFuture(definition));
            await(trees.save(progress));
            await(new JdbcPetDeathWorkRepository(bootstrap).save(
                    new dev.dasannn.neopets.core.pet.PetDeathWork(petId, owner, false, false, Instant.EPOCH)));
            assertEquals(12, await(trees.resetForDeath(owner, TreeProgress.empty(petId, definition),
                    progress.assignedSkillPoints(definition))));
        }
        try (var restarted = new StorageBootstrap(settings)) {
            var trees = new JdbcTreeProgressRepository(restarted,
                    ignored -> CompletableFuture.completedFuture(definition));
            // Even a retry with the original refund must not credit SP twice.
            assertEquals(12, await(trees.resetForDeath(owner, TreeProgress.empty(petId, definition), 5)));
            assertEquals(Optional.empty(), await(trees.load(petId)));
            org.junit.jupiter.api.Assertions.assertTrue(await(new JdbcPetDeathWorkRepository(restarted)
                    .findByPet(petId)).orElseThrow().skillsHandled());
            assertEquals(12, await(new JdbcPlayerRepository(restarted).findById(owner))
                    .orElseThrow().skillPointBalance());
        }
    }

    @Test
    void failedRefundRollsBackProgressAndCheckpoint() throws Exception {
        var settings = new StorageSettings(StorageType.SQLITE, temporaryDirectory.resolve("rollback.db"),
                null, 0, null, null, null, 2, Duration.ofSeconds(5));
        var petId = new PetId(UUID.randomUUID());
        var owner = new PlayerId(UUID.randomUUID());
        var definition = new SkillTreeDefinition(new PetTypeId("dog"), List.of(
                new AttributeDefinition("scent", AttributeCategory.CUSTOM, 1,
                        new CustomEffectReference("scent"), List.of(BigDecimal.ONE))));
        var progress = TreeProgress.empty(petId, definition).withLevels("scent", 1, 1);
        try (var bootstrap = new StorageBootstrap(settings)) {
            await(new JdbcPetRepository(bootstrap).save(new OwnedPet(petId, owner, new VariantId("dog_pale"),
                    Optional.empty(), PetStatus.DEAD, false, Instant.EPOCH)));
            var players = new JdbcPlayerRepository(bootstrap);
            await(players.save(new dev.dasannn.neopets.core.model.PlayerProfile(owner, Integer.MAX_VALUE, List.of())));
            var trees = new JdbcTreeProgressRepository(bootstrap,
                    ignored -> CompletableFuture.completedFuture(definition));
            await(trees.save(progress));
            var work = new JdbcPetDeathWorkRepository(bootstrap);
            await(work.save(new dev.dasannn.neopets.core.pet.PetDeathWork(
                    petId, owner, false, false, Instant.EPOCH)));

            org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> await(trees.resetForDeath(owner, TreeProgress.empty(petId, definition), 1)));

            assertEquals(Optional.of(progress), await(trees.load(petId)));
            org.junit.jupiter.api.Assertions.assertFalse(await(work.findByPet(petId)).orElseThrow().skillsHandled());
            assertEquals(Integer.MAX_VALUE, await(players.findById(owner)).orElseThrow().skillPointBalance());
            await(players.save(new dev.dasannn.neopets.core.model.PlayerProfile(owner, 3, List.of())));
            assertEquals(4, await(trees.resetForDeath(owner, TreeProgress.empty(petId, definition), 1)));
        }
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
