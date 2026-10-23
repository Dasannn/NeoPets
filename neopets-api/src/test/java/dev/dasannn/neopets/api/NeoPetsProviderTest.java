package dev.dasannn.neopets.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeoPetsProviderTest {

    @AfterEach
    void clearProvider() {
        NeoPetsProvider.unregister();
    }

    @Test
    void getFailsWhenNoApiIsRegistered() {
        assertThrows(IllegalStateException.class, NeoPetsProvider::get);
    }

    @Test
    void registerMakesApiAvailable() {
        NeoPetsApi api = new StubApi();

        NeoPetsProvider.register(api);

        assertSame(api, NeoPetsProvider.get());
    }

    @Test
    void registerRejectsNull() {
        assertThrows(NullPointerException.class, () -> NeoPetsProvider.register(null));
    }

    @Test
    void registerRejectsASecondApi() {
        NeoPetsApi first = new StubApi();
        NeoPetsProvider.register(first);

        assertThrows(IllegalStateException.class, () -> NeoPetsProvider.register(new StubApi()));
        assertSame(first, NeoPetsProvider.get());
    }

    @Test
    void unregisterAllowsAReplacementToBeRegistered() {
        NeoPetsProvider.register(new StubApi());

        NeoPetsProvider.unregister();
        NeoPetsApi replacement = new StubApi();
        NeoPetsProvider.register(replacement);

        assertSame(replacement, NeoPetsProvider.get());
    }

    @Test
    void onlyOneConcurrentRegistrationSucceeds() throws Exception {
        int contenderCount = 16;
        CountDownLatch ready = new CountDownLatch(contenderCount);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Boolean>> attempts = java.util.stream.IntStream.range(0, contenderCount)
                    .mapToObj(ignored -> executor.submit(() -> tryRegister(ready, start)))
                    .toList();

            ready.await();
            start.countDown();

            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }
    }

    private static boolean tryRegister(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            NeoPetsProvider.register(new StubApi());
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    private static final class StubApi implements NeoPetsApi {

        @Override
        public Optional<PetView> findLoadedPet(UUID petId) {
            return Optional.empty();
        }

        @Override
        public List<PetView> activePets(UUID ownerId) {
            return List.of();
        }

        @Override
        public CompletableFuture<Optional<PlayerPetsView>> loadPlayerPets(UUID ownerId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<Optional<SkillTreeView>> loadSkillTree(UUID petId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }
}
