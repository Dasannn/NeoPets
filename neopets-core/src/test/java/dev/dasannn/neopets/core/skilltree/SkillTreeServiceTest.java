package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.NodeActivated;
import dev.dasannn.neopets.core.event.NodeUnlocked;
import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.model.RankLimits;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.port.PetRepository;
import dev.dasannn.neopets.core.port.PlayerRepository;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SkillTreeServiceTest {
    private static final PlayerId PLAYER = new PlayerId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final PlayerId OTHER_PLAYER =
            new PlayerId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private static final PetId PET_ID = new PetId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final VariantId VARIANT_ID = new VariantId("dog_brown");
    private static final PetTypeId TYPE_ID = new PetTypeId("dog");
    private static final AttributeDefinition HEALTH = new AttributeDefinition(
            "health",
            AttributeCategory.VANILLA,
            2,
            new VanillaEffectReference("max_health", List.of(new BigDecimal("2.0"), new BigDecimal("4.0"))),
            List.of(new BigDecimal("10.00"), new BigDecimal("20.00")),
            List.of(1, 2));
    private static final AttributeDefinition SPEED = new AttributeDefinition(
            "speed",
            AttributeCategory.VANILLA,
            1,
            new VanillaEffectReference("movement_speed", List.of(new BigDecimal("0.1"))),
            List.of(new BigDecimal("5.00")),
            List.of(1));
    private static final SkillTreeDefinition DEFINITION =
            new SkillTreeDefinition(TYPE_ID, List.of(HEALTH, SPEED));

    @Test
    void unlocksNextLevelWithdrawsItsCostPersistsOnceAndPublishes() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 100.0;
        var events = new ArrayList<NodeUnlocked>();
        fixture.eventBus.subscribe(NodeUnlocked.class, events::add);

        var first = assertInstanceOf(UnlockResult.Unlocked.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());
        var second = assertInstanceOf(UnlockResult.Unlocked.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals(1, first.level());
        assertEquals(new BigDecimal("10.00"), first.moneyCost());
        assertEquals(2, second.level());
        assertEquals(70.0, fixture.economy.balance);
        assertEquals(List.of(10.0, 20.0), fixture.economy.withdrawals);
        assertEquals(2, fixture.progressRepository.saveCount);
        assertEquals(2, fixture.progress().attributes().get("health").unlockedLevel());
        assertEquals(List.of(
                new NodeUnlocked(PET_ID, "health", 1),
                new NodeUnlocked(PET_ID, "health", 2)), events);
    }

    @Test
    void unlockChecksOwnershipAndAliveStatusBeforeLoadingProgress() {
        var missing = new Fixture(null, 0, new RankLimits(1, 10, 10));
        assertInstanceOf(UnlockResult.NotOwned.class, missing.service.unlock(PLAYER, PET_ID, "health").join());

        var wrongOwner = new Fixture(pet(OTHER_PLAYER, PetStatus.ALIVE), 0, new RankLimits(1, 10, 10));
        assertInstanceOf(UnlockResult.NotOwned.class,
                wrongOwner.service.unlock(PLAYER, PET_ID, "health").join());

        var dead = new Fixture(pet(PLAYER, PetStatus.DEAD), 0, new RankLimits(1, 10, 10));
        assertInstanceOf(UnlockResult.PetDead.class, dead.service.unlock(PLAYER, PET_ID, "health").join());

        var transit = new Fixture(pet(PLAYER, PetStatus.IN_TRANSIT), 0, new RankLimits(1, 10, 10));
        assertInstanceOf(UnlockResult.PetInTransit.class,
                transit.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals(0, missing.progressRepository.loadCount);
        assertEquals(0, wrongOwner.progressRepository.loadCount);
        assertEquals(0, dead.progressRepository.loadCount);
        assertEquals(0, transit.progressRepository.loadCount);
    }

    @Test
    void unlockRejectsUnknownAttributeMaximumLevelNodeLimitAndInsufficientFunds() {
        var unknown = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        assertInstanceOf(UnlockResult.UnknownAttribute.class,
                unknown.service.unlock(PLAYER, PET_ID, "armor").join());

        var maximum = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        maximum.progressRepository.progress = Optional.of(progress(2, 0, 0, 0));
        assertInstanceOf(UnlockResult.MaxLevel.class,
                maximum.service.unlock(PLAYER, PET_ID, "health").join());

        var limited = new Fixture(alivePet(), 0, new RankLimits(1, 10, 1));
        limited.progressRepository.progress = Optional.of(progress(1, 0, 0, 0));
        assertInstanceOf(UnlockResult.NodeLimitReached.class,
                limited.service.unlock(PLAYER, PET_ID, "speed").join());

        var poor = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        poor.economy.balance = 9.99;
        var result = assertInstanceOf(UnlockResult.InsufficientFunds.class,
                poor.service.unlock(PLAYER, PET_ID, "health").join());
        assertEquals(new BigDecimal("10.00"), result.required());

        assertEquals(0, unknown.economy.withdrawals.size());
        assertEquals(0, maximum.economy.withdrawals.size());
        assertEquals(0, limited.economy.withdrawals.size());
        assertEquals(0, poor.economy.withdrawals.size());
    }

    @Test
    void refundsMoneyAndDoesNotPublishWhenUnlockPersistenceFails() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 50.0;
        fixture.progressRepository.saveFailure = new IllegalStateException("database unavailable");
        var events = new ArrayList<NodeUnlocked>();
        fixture.eventBus.subscribe(NodeUnlocked.class, events::add);

        var result = assertInstanceOf(UnlockResult.Failed.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals("database unavailable", result.cause().getMessage());
        assertEquals(50.0, fixture.economy.balance);
        assertEquals(List.of(10.0), fixture.economy.withdrawals);
        assertEquals(List.of(10.0), fixture.economy.deposits);
        assertEquals(List.of(), events);
        assertEquals(1, fixture.progressRepository.saveCount);
    }

    @Test
    void reportsEconomyFailureWithoutWritingProgress() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 50.0;
        fixture.economy.withdrawalFailure = "provider offline";

        var result = assertInstanceOf(UnlockResult.Failed.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals("economy withdrawal failed: provider offline", result.cause().getMessage());
        assertEquals(0, fixture.progressRepository.saveCount);
    }

    @Test
    void activatesNextUnlockedLevelSpendsConfiguredPointsPersistsOnceAndPublishes() {
        var fixture = new Fixture(alivePet(), 5, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 0, 0, 0));
        var events = new ArrayList<NodeActivated>();
        fixture.eventBus.subscribe(NodeActivated.class, events::add);

        var first = assertInstanceOf(ActivateResult.Activated.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());
        var second = assertInstanceOf(ActivateResult.Activated.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());

        assertEquals(1, first.skillPointCost());
        assertEquals(2, second.skillPointCost());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(2, fixture.playerRepository.saveCount);
        assertEquals(2, fixture.progressRepository.saveCount);
        assertEquals(2, fixture.progress().attributes().get("health").activeLevel());
        assertEquals(List.of(
                new NodeActivated(PET_ID, "health", 1),
                new NodeActivated(PET_ID, "health", 2)), events);
    }

    @Test
    void activateChecksOwnershipAliveAttributeAndAnUnlockedInactiveLevel() {
        var missing = new Fixture(null, 5, new RankLimits(1, 10, 10));
        assertInstanceOf(ActivateResult.NotOwned.class,
                missing.service.activate(PLAYER, PET_ID, "health").join());

        var dead = new Fixture(pet(PLAYER, PetStatus.DEAD), 5, new RankLimits(1, 10, 10));
        assertInstanceOf(ActivateResult.PetDead.class,
                dead.service.activate(PLAYER, PET_ID, "health").join());

        var transit = new Fixture(pet(PLAYER, PetStatus.IN_TRANSIT), 5, new RankLimits(1, 10, 10));
        assertInstanceOf(ActivateResult.PetInTransit.class,
                transit.service.activate(PLAYER, PET_ID, "health").join());

        var unknown = new Fixture(alivePet(), 5, new RankLimits(1, 10, 10));
        assertInstanceOf(ActivateResult.UnknownAttribute.class,
                unknown.service.activate(PLAYER, PET_ID, "armor").join());

        var noUnlocked = new Fixture(alivePet(), 5, new RankLimits(1, 10, 10));
        assertInstanceOf(ActivateResult.NoUnlockedLevel.class,
                noUnlocked.service.activate(PLAYER, PET_ID, "health").join());
    }

    @Test
    void activateEnforcesAssignedPointLimitBeforePlayerBalance() {
        var fixture = new Fixture(alivePet(), 10, new RankLimits(1, 2, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 1, 0, 0));

        var result = assertInstanceOf(ActivateResult.SkillPointLimitReached.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());

        assertEquals(2, result.maxSkillPointsPerPet());
        assertEquals(0, fixture.playerRepository.findCount);
        assertEquals(0, fixture.progressRepository.saveCount);
    }

    @Test
    void activateRejectsInsufficientUnassignedSkillPoints() {
        var fixture = new Fixture(alivePet(), 1, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 1, 0, 0));

        var result = assertInstanceOf(ActivateResult.InsufficientSkillPoints.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());

        assertEquals(2, result.required());
        assertEquals(1, result.available());
        assertEquals(0, fixture.playerRepository.saveCount);
        assertEquals(0, fixture.progressRepository.saveCount);
    }

    @Test
    void returnsSpentPointsAndDoesNotPublishWhenActivationPersistenceFails() {
        var fixture = new Fixture(alivePet(), 4, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 0, 0, 0));
        fixture.progressRepository.saveFailure = new IllegalStateException("write failed");
        var events = new ArrayList<NodeActivated>();
        fixture.eventBus.subscribe(NodeActivated.class, events::add);

        var result = assertInstanceOf(ActivateResult.Failed.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());

        assertEquals("write failed", result.cause().getMessage());
        assertEquals(4, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(2, fixture.playerRepository.saveCount);
        assertEquals(List.of(), events);
    }

    @Test
    void resetZeroesEveryLevelRefundsAssignedPointsAndNeverTouchesMoney() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 2, 1, 1));
        fixture.economy.balance = 75.0;

        var result = assertInstanceOf(ResetResult.Reset.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals(4, result.refundedSkillPoints());
        assertEquals(6, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(0, fixture.progress().unlockedNodes());
        assertEquals(0, fixture.progress().activeNodes());
        assertEquals(1, fixture.progressRepository.saveCount);
        assertEquals(List.of(), fixture.economy.withdrawals);
        assertEquals(List.of(), fixture.economy.deposits);
    }

    @Test
    void resetCanForfeitPointsAndCanResetADeadPet() {
        var fixture = new Fixture(pet(PLAYER, PetStatus.DEAD), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));

        var result = assertInstanceOf(ResetResult.Reset.class,
                fixture.service.reset(PLAYER, PET_ID, false).join());

        assertEquals(0, result.refundedSkillPoints());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(0, fixture.progress().unlockedNodes());
    }

    @Test
    void resetRejectsAPetThePlayerDoesNotOwn() {
        var missing = new Fixture(null, 2, new RankLimits(1, 10, 10));
        var wrongOwner = new Fixture(pet(OTHER_PLAYER, PetStatus.ALIVE), 2, new RankLimits(1, 10, 10));

        assertInstanceOf(ResetResult.NotOwned.class, missing.service.reset(PLAYER, PET_ID, true).join());
        assertInstanceOf(ResetResult.NotOwned.class, wrongOwner.service.reset(PLAYER, PET_ID, true).join());
        assertEquals(0, missing.progressRepository.loadCount);
        assertEquals(0, wrongOwner.progressRepository.loadCount);
    }

    @Test
    void resetCompensatesPointRefundWhenProgressPersistenceFails() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));
        fixture.progressRepository.saveFailure = new IllegalStateException("write failed");

        var result = assertInstanceOf(ResetResult.Failed.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals("write failed", result.cause().getMessage());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(2, fixture.playerRepository.saveCount);
        assertEquals(1, fixture.progress().activeNodes());
    }

    @Test
    void sequencesOverlappingUnlockThenActivateForOnePlayer() {
        var fixture = new Fixture(alivePet(), 3, new RankLimits(1, 10, 10));
        fixture.economy.balance = 100.0;
        fixture.progressRepository.delayNextSave();

        var unlock = fixture.service.unlock(PLAYER, PET_ID, "health");
        var activate = fixture.service.activate(PLAYER, PET_ID, "health");

        assertFalse(unlock.isDone());
        assertFalse(activate.isDone());
        assertEquals(1, fixture.progressRepository.loadCount);
        fixture.progressRepository.completeDelayedSave();

        assertInstanceOf(UnlockResult.Unlocked.class, unlock.join());
        assertInstanceOf(ActivateResult.Activated.class, activate.join());
        assertEquals(2, fixture.progressRepository.loadCount);
        assertEquals(1, fixture.progress().attributes().get("health").unlockedLevel());
        assertEquals(1, fixture.progress().attributes().get("health").activeLevel());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
    }

    @Test
    void sequencesOverlappingUnlocksWithoutLosingALevel() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 100.0;
        fixture.progressRepository.delayNextSave();

        var first = fixture.service.unlock(PLAYER, PET_ID, "health");
        var second = fixture.service.unlock(PLAYER, PET_ID, "health");
        assertFalse(second.isDone());

        fixture.progressRepository.completeDelayedSave();

        assertEquals(1, assertInstanceOf(UnlockResult.Unlocked.class, first.join()).level());
        assertEquals(2, assertInstanceOf(UnlockResult.Unlocked.class, second.join()).level());
        assertEquals(2, fixture.progress().attributes().get("health").unlockedLevel());
        assertEquals(2, fixture.progressRepository.saveCount);
        assertEquals(70.0, fixture.economy.balance);
    }

    @Test
    void infrastructureFailuresAreResultsAndInvalidArgumentsFailFast() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.progressRepository.loadFailure = new IllegalStateException("read failed");

        assertInstanceOf(UnlockResult.Failed.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());
        assertInstanceOf(ActivateResult.Failed.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());
        assertInstanceOf(ResetResult.Failed.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertThrows(NullPointerException.class, () -> fixture.service.unlock(null, PET_ID, "health"));
        assertThrows(NullPointerException.class, () -> fixture.service.unlock(PLAYER, null, "health"));
        assertThrows(NullPointerException.class, () -> fixture.service.unlock(PLAYER, PET_ID, null));
    }

    private static OwnedPet alivePet() {
        return pet(PLAYER, PetStatus.ALIVE);
    }

    private static OwnedPet pet(PlayerId owner, PetStatus status) {
        return new OwnedPet(PET_ID, owner, VARIANT_ID, Optional.empty(), status, false, Instant.EPOCH);
    }

    private static TreeProgress progress(int healthUnlocked, int healthActive, int speedUnlocked, int speedActive) {
        return TreeProgress.empty(PET_ID, DEFINITION)
                .withLevels("health", healthUnlocked, healthActive)
                .withLevels("speed", speedUnlocked, speedActive);
    }

    private static final class Fixture {
        private final InMemoryPetRepository petRepository;
        private final InMemoryProgressRepository progressRepository = new InMemoryProgressRepository();
        private final InMemoryPlayerRepository playerRepository;
        private final FakeEconomy economy = new FakeEconomy();
        private final DomainEventBus eventBus = new DomainEventBus();
        private final SkillTreeService service;

        private Fixture(OwnedPet pet, int skillPoints, RankLimits limits) {
            petRepository = new InMemoryPetRepository(pet);
            playerRepository = new InMemoryPlayerRepository(new PlayerProfile(PLAYER, skillPoints, List.of()));
            var skillPointsService = new SkillPointService(playerRepository, eventBus);
            service = new SkillTreeService(
                    petRepository,
                    progressRepository,
                    new InMemorySkillTreeRegistry(List.of(DEFINITION)),
                    variantId -> variantId.equals(VARIANT_ID) ? Optional.of(TYPE_ID) : Optional.empty(),
                    economy,
                    skillPointsService,
                    ignored -> limits,
                    eventBus);
        }

        private TreeProgress progress() {
            return progressRepository.progress.orElseThrow();
        }
    }

    private static final class InMemoryPetRepository implements PetRepository {
        private final OwnedPet pet;

        private InMemoryPetRepository(OwnedPet pet) {
            this.pet = pet;
        }

        @Override
        public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(pet)
                    .filter(value -> value.id().equals(petId)));
        }

        @Override
        public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
            return CompletableFuture.completedFuture(
                    pet != null && pet.owner().equals(ownerId) ? List.of(pet) : List.of());
        }

        @Override
        public CompletableFuture<Void> save(OwnedPet pet) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<Void> deleteById(PetId petId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryProgressRepository implements TreeProgressRepository {
        private Optional<TreeProgress> progress = Optional.empty();
        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private int loadCount;
        private int saveCount;
        private boolean delayNextSave;
        private TreeProgress delayedProgress;
        private CompletableFuture<Void> delayedSave;

        @Override
        public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
            loadCount++;
            if (loadFailure != null) {
                return CompletableFuture.failedFuture(loadFailure);
            }
            return CompletableFuture.completedFuture(progress.filter(value -> value.petId().equals(petId)));
        }

        @Override
        public CompletableFuture<Void> save(TreeProgress progress) {
            saveCount++;
            if (saveFailure != null) {
                return CompletableFuture.failedFuture(saveFailure);
            }
            if (delayNextSave) {
                delayNextSave = false;
                delayedProgress = progress;
                delayedSave = new CompletableFuture<>();
                return delayedSave;
            }
            this.progress = Optional.of(progress);
            return CompletableFuture.completedFuture(null);
        }

        private void delayNextSave() {
            delayNextSave = true;
        }

        private void completeDelayedSave() {
            progress = Optional.of(delayedProgress);
            delayedSave.complete(null);
        }
    }

    private static final class InMemoryPlayerRepository implements PlayerRepository {
        private PlayerProfile profile;
        private int findCount;
        private int saveCount;

        private InMemoryPlayerRepository(PlayerProfile profile) {
            this.profile = profile;
        }

        @Override
        public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
            findCount++;
            return CompletableFuture.completedFuture(Optional.of(profile)
                    .filter(value -> value.playerId().equals(playerId)));
        }

        @Override
        public CompletableFuture<Void> save(PlayerProfile profile) {
            this.profile = profile;
            saveCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeEconomy implements Economy {
        private double balance;
        private String withdrawalFailure;
        private final List<Double> withdrawals = new ArrayList<>();
        private final List<Double> deposits = new ArrayList<>();

        @Override
        public boolean has(PlayerId playerId, double amount) {
            return balance >= amount;
        }

        @Override
        public EconomyResult withdraw(PlayerId playerId, double amount) {
            if (withdrawalFailure != null) {
                return new EconomyResult.Failure(withdrawalFailure);
            }
            balance -= amount;
            withdrawals.add(amount);
            return new EconomyResult.Success(balance);
        }

        @Override
        public EconomyResult deposit(PlayerId playerId, double amount) {
            balance += amount;
            deposits.add(amount);
            return new EconomyResult.Success(balance);
        }
    }
}
