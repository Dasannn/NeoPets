package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.platform.paper.LivePetRegistry;
import dev.dasannn.neopets.storage.cache.PlayerDataCache;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

class RuntimePlaceholderDataSourceTest {
    private static final PlayerId PLAYER = new PlayerId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final OwnedPet FIRST = pet("20000000-0000-0000-0000-000000000001", "dog_pale", null);
    private static final OwnedPet SECOND = pet("20000000-0000-0000-0000-000000000002", "cat_black", "Luna");

    @Test
    void readsCountsLimitsAndFirstSummonedNameOnlyFromLoadedSnapshots() {
        var cache = new PlayerDataCache(players(), pets(), trees());
        cache.load(PLAYER).join();
        var livePets = new LivePetRegistry();
        livePets.register(SECOND.id(), UUID.randomUUID());
        var source = new RuntimePlaceholderDataSource(
                cache, livePets, ignored -> new RankLimits(4, 6, 16),
                pet -> pet.customName().orElse(pet.variant().value()), List.of(PLAYER.value()));

        assertEquals(Optional.of(new PlaceholderSnapshot(
                17, 6, 2, 1, 4, Optional.of("Luna"))), source.snapshot(PLAYER.value()));

        cache.unload(PLAYER).join();
        assertTrue(source.snapshot(PLAYER.value()).isEmpty());
    }

    @Test
    void offlineLoadedPlayerIsHiddenBeforeJoinAndImmediatelyAfterQuit() throws Exception {
        var cache = new PlayerDataCache(players(), pets(), trees());
        cache.load(PLAYER).join();
        var source = new RuntimePlaceholderDataSource(
                cache, new LivePetRegistry(), ignored -> new RankLimits(1, 3, 10),
                pet -> pet.variant().value(), List.of());
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER.value();
                    case "getName" -> "PlaceholderPlayer";
                    default -> throw new AssertionError("unexpected Bukkit call: " + method.getName());
                });

        assertTrue(source.snapshot(PLAYER.value()).isEmpty());
        source.onJoin(new PlayerJoinEvent(player, Component.empty()));
        var snapshot = CompletableFuture.supplyAsync(() -> source.snapshot(PLAYER.value()))
                .get(1, TimeUnit.SECONDS).orElseThrow();
        assertEquals(17, snapshot.skillPoints());
        assertEquals(0, snapshot.activePets());
        assertTrue(snapshot.firstActivePetName().isEmpty());

        source.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        assertTrue(cache.isLoaded(PLAYER));
        assertTrue(source.snapshot(PLAYER.value()).isEmpty());
    }

    @Test
    void firstLivePetUsesOwnershipOrderAndDoesNotCountPersistedActiveFlags() {
        var cache = new PlayerDataCache(players(), pets(), trees());
        cache.load(PLAYER).join();
        var livePets = new LivePetRegistry();
        livePets.register(SECOND.id(), UUID.randomUUID());
        livePets.register(FIRST.id(), UUID.randomUUID());
        var source = new RuntimePlaceholderDataSource(
                cache, livePets, ignored -> new RankLimits(4, 6, 16),
                pet -> pet.customName().orElse(pet.variant().value()), List.of(PLAYER.value()));

        assertEquals(Optional.of(new PlaceholderSnapshot(
                17, 6, 2, 2, 4, Optional.of("dog_pale"))), source.snapshot(PLAYER.value()));
        livePets.unregister(FIRST.id());
        assertEquals(Optional.of(new PlaceholderSnapshot(
                17, 6, 2, 1, 4, Optional.of("Luna"))), source.snapshot(PLAYER.value()));
    }

    private static PlayerRepository players() {
        return new PlayerRepository() {
            @Override
            public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
                return CompletableFuture.completedFuture(Optional.of(new PlayerProfile(playerId, 17, List.of())));
            }

            @Override
            public CompletableFuture<Void> save(PlayerProfile profile) {
                throw new AssertionError("placeholder reads must not save player data");
            }
        };
    }

    private static PetRepository pets() {
        return new PetRepository() {
            @Override
            public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
                throw new AssertionError("placeholder reads must not query pets by id");
            }

            @Override
            public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
                return CompletableFuture.completedFuture(List.of(FIRST, SECOND));
            }

            @Override
            public CompletableFuture<Void> save(OwnedPet pet) {
                throw new AssertionError("placeholder reads must not save pets");
            }

            @Override
            public CompletableFuture<Void> deleteById(PetId petId) {
                throw new AssertionError("placeholder reads must not delete pets");
            }
        };
    }

    private static TreeProgressRepository trees() {
        return new TreeProgressRepository() {
            @Override
            public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletableFuture<Void> save(TreeProgress progress) {
                throw new AssertionError("placeholder reads must not save tree progress");
            }
        };
    }

    private static OwnedPet pet(String petId, String variantId, String name) {
        return new OwnedPet(new PetId(UUID.fromString(petId)), PLAYER, new VariantId(variantId),
                Optional.ofNullable(name), PetStatus.ALIVE, true, Instant.EPOCH);
    }
}
