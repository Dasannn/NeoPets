package dev.dasannn.neopets.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.pet.VariantCatalog;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.port.PetEntityGateway;
import java.util.concurrent.CompletableFuture;
import java.time.Clock;
import dev.dasannn.neopets.core.pet.PetService;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.pet.DeathResult;
import dev.dasannn.neopets.core.pet.SoulItemService;
import dev.dasannn.neopets.core.pet.ReviveResult;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.DuplicatePetVariantException;
import dev.dasannn.neopets.core.port.SaddlebagVersionConflictException;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.core.pet.PetDeathWork;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

abstract class AbstractJdbcRepositoryTest {
    private static final PlayerId OWNER = new PlayerId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final PlayerId OTHER_OWNER =
            new PlayerId(UUID.fromString("10000000-0000-0000-0000-000000000002"));
    private static final PetId PET_ID = new PetId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final PetId OTHER_PET_ID =
            new PetId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-23T12:34:56.123Z");

    private StorageBootstrap bootstrap;
    private JdbcPetRepository pets;
    private JdbcPlayerRepository players;
    private JdbcSoulTokenRepository tokens;
    private JdbcPetStorageRepository petStorage;
    private JdbcPendingSoulDeliveryRepository pendingDeliveries;
    private JdbcSaddlebagRepository saddlebags;
    private JdbcPetDeathWorkRepository deathWork;

    abstract StorageSettings settings();

    void beforeBootstrap() throws Exception {
    }

    @BeforeEach
    final void createRepositories() throws Exception {
        beforeBootstrap();
        bootstrap = new StorageBootstrap(settings());
        pets = new JdbcPetRepository(bootstrap);
        players = new JdbcPlayerRepository(bootstrap);
        tokens = new JdbcSoulTokenRepository(bootstrap);
        petStorage = new JdbcPetStorageRepository(bootstrap);
        pendingDeliveries = new JdbcPendingSoulDeliveryRepository(bootstrap);
        saddlebags = new JdbcSaddlebagRepository(bootstrap);
        deathWork = new JdbcPetDeathWorkRepository(bootstrap);
    }

    @AfterEach
    final void closeStorage() {
        if (bootstrap != null) {
            bootstrap.close();
        }
    }

    @Test
    final void playerRepositoryCreatesReadsAndUpdatesBalancesWithoutPersistingPets() throws Exception {
        assertEquals(Optional.empty(), await(players.findById(OWNER)));

        var ownedPet = pet(PET_ID, OWNER, "dog_brown");
        await(players.save(new PlayerProfile(OWNER, 7, List.of(ownedPet))));
        assertEquals(Optional.of(new PlayerProfile(OWNER, 7, List.of())), await(players.findById(OWNER)));
        assertEquals(Optional.empty(), await(pets.findById(PET_ID)));

        await(players.save(new PlayerProfile(OWNER, 13, List.of())));
        assertEquals(13, await(players.findById(OWNER)).orElseThrow().skillPointBalance());
    }

    @Test
    final void petRepositoryProvidesFullCrud() throws Exception {
        var original = pet(PET_ID, OWNER, "cat_black");
        var second = pet(OTHER_PET_ID, OWNER, "dog_brown");

        assertEquals(Optional.empty(), await(pets.findById(PET_ID)));
        assertEquals(List.of(), await(pets.findByOwner(OWNER)));

        await(pets.save(original));
        await(pets.save(second));
        assertEquals(Optional.of(original), await(pets.findById(PET_ID)));
        assertEquals(Set.of(original, second), Set.copyOf(await(pets.findByOwner(OWNER))));

        var updated = new OwnedPet(
                PET_ID,
                OTHER_OWNER,
                new VariantId("parrot_red"),
                Optional.of("Scout"),
                PetStatus.IN_TRANSIT,
                true,
                CREATED_AT.plusMillis(1));
        await(pets.save(updated));

        assertEquals(Optional.of(updated), await(pets.findById(PET_ID)));
        assertEquals(List.of(second), await(pets.findByOwner(OWNER)));
        assertEquals(List.of(updated), await(pets.findByOwner(OTHER_OWNER)));

        await(pets.deleteById(PET_ID));
        await(pets.deleteById(PET_ID));
        assertEquals(Optional.empty(), await(pets.findById(PET_ID)));
    }

    @Test
    final void duplicateOwnerAndVariantMapsToDomainConflictWithoutReplacingEitherPet() throws Exception {
        var original = pet(PET_ID, OWNER, "dog_brown");
        var duplicate = pet(OTHER_PET_ID, OWNER, "dog_brown");
        await(pets.save(original));

        var exception = assertThrows(ExecutionException.class, () -> await(pets.save(duplicate)));
        var conflict = assertInstanceOf(DuplicatePetVariantException.class, exception.getCause());
        assertEquals(OWNER, conflict.ownerId());
        assertEquals(new VariantId("dog_brown"), conflict.variantId());
        assertEquals(Optional.of(original), await(pets.findById(PET_ID)));
        assertEquals(Optional.empty(), await(pets.findById(OTHER_PET_ID)));
    }

    @Test
    final void updatingAPetToAnOwnedVariantMapsToDomainConflict() throws Exception {
        var first = pet(PET_ID, OWNER, "dog_brown");
        var second = pet(OTHER_PET_ID, OWNER, "dog_black");
        await(pets.save(first));
        await(pets.save(second));

        var conflictingUpdate = new OwnedPet(
                OTHER_PET_ID,
                OWNER,
                new VariantId("dog_brown"),
                Optional.empty(),
                PetStatus.DEAD,
                false,
                CREATED_AT);
        var exception = assertThrows(ExecutionException.class, () -> await(pets.save(conflictingUpdate)));
        assertInstanceOf(DuplicatePetVariantException.class, exception.getCause());
        assertEquals(Optional.of(second), await(pets.findById(OTHER_PET_ID)));
    }

    @Test
    final void petStorageSnapshotsWritesReturnsIndependentReadsAndDeletes() throws Exception {
        await(pets.save(pet(PET_ID, OWNER, "donkey_brown")));
        assertEquals(Optional.empty(), await(petStorage.load(PET_ID)));

        var contents = new byte[] {1, 2, 3};
        var pendingSave = petStorage.save(PET_ID, contents);
        contents[0] = 99;
        await(pendingSave);

        var firstRead = await(petStorage.load(PET_ID)).orElseThrow();
        assertArrayEquals(new byte[] {1, 2, 3}, firstRead);
        firstRead[1] = 88;
        assertArrayEquals(new byte[] {1, 2, 3}, await(petStorage.load(PET_ID)).orElseThrow());

        await(petStorage.save(PET_ID, new byte[] {4, 5}));
        assertArrayEquals(new byte[] {4, 5}, await(petStorage.load(PET_ID)).orElseThrow());
        await(petStorage.deleteById(PET_ID));
        await(petStorage.deleteById(PET_ID));
        assertEquals(Optional.empty(), await(petStorage.load(PET_ID)));
    }

    @Test
    final void saddlebagRoundTripsAndRejectsAStaleOptimisticVersion() throws Exception {
        await(pets.save(pet(PET_ID, OWNER, "donkey_brown")));
        var created = await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {1, 2}));
        assertArrayEquals(new byte[] {1, 2}, created.contents());
        assertEquals(0, created.version());

        var updated = await(saddlebags.save(PET_ID, OWNER, new byte[] {3, 4}, created.version()));
        assertEquals(1, updated.version());
        assertArrayEquals(new byte[] {3, 4}, updated.contents());
        var loaded = await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {9}));
        assertEquals(updated, loaded);

        var conflict = assertThrows(ExecutionException.class,
                () -> await(saddlebags.save(PET_ID, OWNER, new byte[] {5}, created.version())));
        assertInstanceOf(SaddlebagVersionConflictException.class, conflict.getCause());
        assertEquals(updated, await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {9})));
    }

    @Test
    final void saddlebagSupportsMetadataLargerThanMysqlBlobLimit() throws Exception {
        await(pets.save(pet(PET_ID, OWNER, "donkey_brown")));
        await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[0]));
        byte[] contents = new byte[128 * 1024];
        new java.util.Random(26).nextBytes(contents);
        await(saddlebags.save(PET_ID, OWNER, contents, 0));
        assertArrayEquals(contents, await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[0])).contents());
    }

    @Test
    final void petDeathKeepsSaddlebagContentsForRevival() throws Exception {
        OwnedPet alive = pet(PET_ID, OWNER, "donkey_brown");
        await(pets.save(alive));
        await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {7, 8, 9}));

        var catalog = new VariantCatalog() {
            public Optional<PetVariant> findById(VariantId id) {
                return Optional.empty();
            }
            public List<PetVariant> findAll() { return List.of(); }
            public List<PetVariant> findByType(
                    PetTypeId type) { return List.of(); }
        };
        var despawned = new java.util.ArrayList<PetId>();
        var entities = new PetEntityGateway() {
            public CompletableFuture<Void> spawn(PetId id) {
                throw new AssertionError("death/revive must not spawn entities");
            }
            public CompletableFuture<Void> despawn(PetId id) {
                despawned.add(id);
                return CompletableFuture.completedFuture(null);
            }
            public CompletableFuture<Void> applyEffects(PetId id) {
                throw new AssertionError("death/revive must not apply effects");
            }
        };
        var clock = Clock.systemUTC();
        var service = new PetService(catalog, pets, entities,
                ignored -> { throw new AssertionError("death/revive must not resolve ranks"); },
                new DomainEventBus(), () -> 0.0, clock);
        assertInstanceOf(DeathResult.Died.class, await(service.die(OWNER, PET_ID)));
        assertEquals(List.of(PET_ID), despawned);

        assertArrayEquals(new byte[] {7, 8, 9},
                await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {0})).contents());
        var souls = new SoulItemService(tokens, clock);
        var soul = await(souls.issue(PET_ID, OWNER));
        assertInstanceOf(ReviveResult.Revived.class,
                await(service.revive(OWNER, PET_ID, soul.tokenId(), OWNER, souls)));
        assertArrayEquals(new byte[] {7, 8, 9},
                await(saddlebags.loadOrCreate(PET_ID, OWNER, new byte[] {0})).contents());
    }

    @Test
    final void storageRequiresAnExistingPetAndPetDeletionCascadesToChildren() throws Exception {
        var missingSave = assertThrows(
                ExecutionException.class, () -> await(petStorage.save(PET_ID, new byte[] {1})));
        assertInstanceOf(java.sql.SQLException.class, missingSave.getCause());

        await(pets.save(pet(PET_ID, OWNER, "donkey_brown")));
        await(petStorage.save(PET_ID, new byte[] {1, 2}));
        try (var connection = bootstrap.dataSource().getConnection();
                var statement = connection.prepareStatement("""
                        INSERT INTO np_pet_attribute
                            (pet_id, attribute_id, unlocked_level, active_level)
                        VALUES (?, 'armor', 2, 1)
                        """)) {
            statement.setString(1, PET_ID.toString());
            statement.executeUpdate();
        }

        await(pets.deleteById(PET_ID));

        assertEquals(Optional.empty(), await(petStorage.load(PET_ID)));
        try (var connection = bootstrap.dataSource().getConnection();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM np_pet_attribute WHERE pet_id = ?")) {
            statement.setString(1, PET_ID.toString());
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    final void soulTokensValidateIdentityAndCanOnlyBeConsumedOnce() throws Exception {
        var tokenId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        var issuedAt = Instant.parse("2026-10-23T13:00:00.456Z");
        await(tokens.issue(tokenId, PET_ID, OWNER, issuedAt));

        assertEquals(Optional.of(new dev.dasannn.neopets.core.pet.SoulToken(
                tokenId, PET_ID, OWNER, issuedAt)), await(tokens.findUnconsumedByPet(PET_ID)));
        assertFalse(await(tokens.wasDelivered(tokenId, PET_ID, OWNER)));
        assertTrue(await(tokens.markDelivered(tokenId, PET_ID, OWNER, issuedAt.plusMillis(1))));
        assertFalse(await(tokens.markDelivered(tokenId, PET_ID, OWNER, issuedAt.plusMillis(2))));
        assertTrue(await(tokens.wasDelivered(tokenId, PET_ID, OWNER)));

        assertTrue(await(tokens.isUsable(tokenId, PET_ID, OWNER)));
        assertFalse(await(tokens.isUsable(tokenId, OTHER_PET_ID, OWNER)));
        assertFalse(await(tokens.isUsable(tokenId, PET_ID, OTHER_OWNER)));
        assertFalse(await(tokens.isUsable(UUID.randomUUID(), PET_ID, OWNER)));

        assertFalse(await(tokens.consume(tokenId, PET_ID, OTHER_OWNER, issuedAt.plusSeconds(1))));
        var firstConsumption = tokens.consume(tokenId, PET_ID, OWNER, issuedAt.plusSeconds(2));
        var competingConsumption = tokens.consume(tokenId, PET_ID, OWNER, issuedAt.plusSeconds(3));
        var successfulConsumptions = (await(firstConsumption) ? 1 : 0)
                + (await(competingConsumption) ? 1 : 0);
        assertEquals(1, successfulConsumptions);
        assertFalse(await(tokens.consume(tokenId, PET_ID, OWNER, issuedAt.plusSeconds(3))));
        assertFalse(await(tokens.isUsable(tokenId, PET_ID, OWNER)));
        assertEquals(Optional.empty(), await(tokens.findUnconsumedByPet(PET_ID)));
    }

    @Test
    final void duplicateSoulTokenIdentifiersFailWithoutChangingTheOriginal() throws Exception {
        var tokenId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        await(tokens.issue(tokenId, PET_ID, OWNER, CREATED_AT));

        var exception = assertThrows(
                ExecutionException.class,
                () -> await(tokens.issue(tokenId, OTHER_PET_ID, OTHER_OWNER, CREATED_AT)));
        assertInstanceOf(java.sql.SQLException.class, exception.getCause());
        assertTrue(await(tokens.isUsable(tokenId, PET_ID, OWNER)));
        assertFalse(await(tokens.isUsable(tokenId, OTHER_PET_ID, OTHER_OWNER)));
    }

    @Test
    final void pendingSoulDeliveriesPersistByOwnerUntilExplicitlyDelivered() throws Exception {
        var firstToken = UUID.fromString("30000000-0000-0000-0000-000000000010");
        var secondToken = UUID.fromString("30000000-0000-0000-0000-000000000011");
        var otherToken = UUID.fromString("30000000-0000-0000-0000-000000000012");
        var first = new PendingSoulDelivery(firstToken, PET_ID, OWNER, CREATED_AT);
        var second = new PendingSoulDelivery(secondToken, OTHER_PET_ID, OWNER, CREATED_AT.plusSeconds(1));
        var other = new PendingSoulDelivery(otherToken, OTHER_PET_ID, OTHER_OWNER, CREATED_AT);

        await(pendingDeliveries.save(second));
        await(pendingDeliveries.save(first));
        await(pendingDeliveries.save(other));
        await(pendingDeliveries.save(first));

        assertEquals(List.of(first, second), await(pendingDeliveries.findByOwner(OWNER)));
        assertEquals(List.of(other), await(pendingDeliveries.findByOwner(OTHER_OWNER)));
        assertTrue(await(pendingDeliveries.claim(firstToken)));
        assertFalse(await(pendingDeliveries.claim(firstToken)));
        await(pendingDeliveries.releaseClaims(OTHER_OWNER));
        assertFalse(await(pendingDeliveries.claim(firstToken)));
        await(pendingDeliveries.releaseClaims(OWNER));
        assertTrue(await(pendingDeliveries.claim(firstToken)));
        await(pendingDeliveries.delete(firstToken));
        await(pendingDeliveries.delete(firstToken));
        assertEquals(List.of(second), await(pendingDeliveries.findByOwner(OWNER)));
    }

    @Test
    final void deathWorkPersistsPolicyAndSkillCheckpointByPet() throws Exception {
        var started = new PetDeathWork(PET_ID, OWNER, false, false, CREATED_AT);
        await(deathWork.save(started));

        assertEquals(Optional.of(started), await(deathWork.findByPet(PET_ID)));
        assertEquals(List.of(started), await(deathWork.findByOwner(OWNER)));

        var handled = started.withSkillsHandled().withCompleted();
        await(deathWork.save(handled));
        assertEquals(Optional.of(handled), await(deathWork.findByPet(PET_ID)));
        assertEquals(List.of(), await(deathWork.findByOwner(OTHER_OWNER)));
    }

    private static OwnedPet pet(PetId id, PlayerId owner, String variant) {
        return new OwnedPet(
                id,
                owner,
                new VariantId(variant),
                Optional.empty(),
                PetStatus.ALIVE,
                false,
                CREATED_AT);
    }

    private static <T> T await(CompletableFuture<T> future) throws Exception {
        return future.get(10, TimeUnit.SECONDS);
    }
}
