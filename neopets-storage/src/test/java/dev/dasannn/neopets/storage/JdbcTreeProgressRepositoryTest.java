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

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
