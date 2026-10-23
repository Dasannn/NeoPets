package dev.dasannn.neopets.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NeoPetsProviderTest {

    private static final long CONCURRENCY_TIMEOUT_SECONDS = 5;

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

            boolean allContendersReady;
            try {
                allContendersReady = ready.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                start.countDown();
            }
            assertTrue(allContendersReady, "concurrent registration tasks did not become ready");

            long successes = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        }
    }

    private static boolean tryRegister(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(CONCURRENCY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timed out waiting to start concurrent registration");
        }
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
