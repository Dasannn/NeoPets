package dev.dasannn.neopets.plugin.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NeoPetsApiImplTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void synchronousMissesNeverConsultPersistentRepositories() throws Exception {
        var repositories = new CountingRepositories();
        var cache = new PlayerDataCache(repositories, repositories, repositories);
        var api = new NeoPetsApiImpl(repositories, repositories, repositories, cache,
                new LivePetRegistry(), new ConfigurationManager(new ConfigurationLoader(temporaryDirectory)));

        assertTrue(api.findLoadedPet(UUID.randomUUID()).isEmpty());
        assertEquals(List.of(), api.activePets(UUID.randomUUID()));
        assertEquals(0, repositories.playerReads);
        assertEquals(0, repositories.petReads);
        assertEquals(0, repositories.treeReads);
    }

    private static final class CountingRepositories
            implements PlayerRepository, PetRepository, TreeProgressRepository {
        private int playerReads;
        private int petReads;
        private int treeReads;

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
            playerReads++;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Void> save(PlayerProfile profile) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            petReads++;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            petReads++;
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet pet) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
            treeReads++;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Void> save(TreeProgress progress) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
