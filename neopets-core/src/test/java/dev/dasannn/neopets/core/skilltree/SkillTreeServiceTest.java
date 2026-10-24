package dev.dasannn.neopets.core.skilltree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.concurrent.PlayerOperationQueue;
import dev.dasannn.neopets.core.event.DomainEventBus;
import dev.dasannn.neopets.core.event.NodeActivated;
import dev.dasannn.neopets.core.event.NodeUnlocked;
import dev.dasannn.neopets.core.event.TreeReset;
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
import java.util.concurrent.CompletionException;
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
    private static final AttributeDefinition FREE = new AttributeDefinition(
            "free",
            AttributeCategory.CUSTOM,
            1,
            new CustomEffectReference("free", Optional.empty()),
            List.of(BigDecimal.ZERO),
            List.of(0));
    private static final SkillTreeDefinition DEFINITION =
            new SkillTreeDefinition(TYPE_ID, List.of(HEALTH, SPEED, FREE));

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
    void reportsFailedMoneyCompensationWithoutHidingThePersistenceFailure() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 50.0;
        fixture.progressRepository.saveFailure = new IllegalStateException("write failed");
        fixture.economy.depositFailure = "refund provider offline";

        var result = assertInstanceOf(UnlockResult.Failed.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals("write failed", result.cause().getMessage());
        assertEquals(1, result.cause().getSuppressed().length);
        assertEquals("economy refund failed: refund provider offline",
                result.cause().getSuppressed()[0].getMessage());
        assertEquals(40.0, fixture.economy.balance);
    }

    @Test
    void waitsForAsynchronousMoneyCompensationBeforeStartingTheNextOperation() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 50.0;
        fixture.progressRepository.saveFailure = new IllegalStateException("write failed");
        fixture.progressRepository.failSaveNumber = 1;
        fixture.economy.delayNextDeposit();

        var failedUnlock = fixture.service.unlock(PLAYER, PET_ID, "health");
        var queuedUnlock = fixture.service.unlock(PLAYER, PET_ID, "health");

        assertFalse(failedUnlock.isDone());
        assertFalse(queuedUnlock.isDone());
        assertEquals(40.0, fixture.economy.balance);
        assertEquals(List.of(10.0), fixture.economy.withdrawals);
        fixture.economy.completeDelayedDeposit();

        assertInstanceOf(UnlockResult.Failed.class, failedUnlock.join());
        assertInstanceOf(UnlockResult.Unlocked.class, queuedUnlock.join());
        assertEquals(List.of(10.0, 10.0), fixture.economy.withdrawals);
        assertEquals(List.of(10.0), fixture.economy.deposits);
        assertEquals(40.0, fixture.economy.balance);
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
    void reportsBalanceCheckFailureWithoutAttemptingAWithdrawal() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balanceFailure = "economy unavailable";

        var result = assertInstanceOf(UnlockResult.Failed.class,
                fixture.service.unlock(PLAYER, PET_ID, "health").join());

        assertEquals("economy balance check failed: economy unavailable", result.cause().getMessage());
        assertEquals(List.of(), fixture.economy.withdrawals);
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
    void activatesAZeroCostLevelWithoutReadingOrWritingThePointBalance() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(
                progress(0, 0, 0, 0).withLevels("free", 1, 0));

        var activation = fixture.service.activate(PLAYER, PET_ID, "free");

        assertTrue(activation.isDone(), "zero-cost activation must not queue a nested SP operation");
        var result = assertInstanceOf(ActivateResult.Activated.class, activation.join());
        assertEquals(0, result.skillPointCost());
        assertEquals(1, fixture.progress().attributes().get("free").activeLevel());
        assertEquals(0, fixture.playerRepository.findCount);
        assertEquals(0, fixture.playerRepository.saveCount);
    }

    @Test
    void activationDoesNotSelfDeadlockOnTheSharedPlayerQueue() {
        var fixture = new Fixture(alivePet(), 1, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 0, 0, 0));

        var activation = fixture.service.activate(PLAYER, PET_ID, "health");

        assertTrue(activation.isDone(), "activation must use the unsequenced SP primitive");
        assertInstanceOf(ActivateResult.Activated.class, activation.join());
        assertEquals(0, fixture.playerRepository.profile.skillPointBalance());
    }

    @Test
    void reportsFailedPointCompensationWithoutHidingThePersistenceFailure() {
        var fixture = new Fixture(alivePet(), 4, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 0, 0, 0));
        fixture.progressRepository.saveFailure = new IllegalStateException("tree write failed");
        fixture.playerRepository.saveFailure = new IllegalStateException("SP refund failed");
        fixture.playerRepository.failSaveNumber = 2;

        var result = assertInstanceOf(ActivateResult.Failed.class,
                fixture.service.activate(PLAYER, PET_ID, "health").join());

        assertEquals("tree write failed", result.cause().getMessage());
        assertEquals(1, result.cause().getSuppressed().length);
        assertEquals("SP refund failed", result.cause().getSuppressed()[0].getMessage());
        assertEquals(3, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(2, fixture.playerRepository.saveCount);
    }

    @Test
    void resetZeroesEveryLevelRefundsAssignedPointsAndNeverTouchesMoney() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 2, 1, 1));
        fixture.economy.balance = 75.0;
        var events = new ArrayList<TreeReset>();
        fixture.eventBus.subscribe(TreeReset.class, events::add);

        var result = assertInstanceOf(ResetResult.Reset.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals(4, result.refundedSkillPoints());
        assertEquals(6, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(0, fixture.progress().unlockedNodes());
        assertEquals(0, fixture.progress().activeNodes());
        assertEquals(1, fixture.progressRepository.saveCount);
        assertEquals(List.of(), fixture.economy.withdrawals);
        assertEquals(List.of(), fixture.economy.deposits);
        assertEquals(List.of(new TreeReset(PET_ID)), events);
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
    void resetDoesNotExposeRefundedPointsWhenProgressPersistenceFails() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));
        fixture.progressRepository.saveFailure = new IllegalStateException("write failed");
        var events = new ArrayList<TreeReset>();
        fixture.eventBus.subscribe(TreeReset.class, events::add);

        var result = assertInstanceOf(ResetResult.Failed.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals("write failed", result.cause().getMessage());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(0, fixture.playerRepository.saveCount);
        assertEquals(1, fixture.progress().activeNodes());
        assertEquals(List.of(), events);
    }

    @Test
    void resetRestoresTreeWhenCreditingSkillPointsFails() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));
        fixture.playerRepository.saveFailure = new IllegalStateException("SP refund failed");
        fixture.playerRepository.failSaveNumber = 1;

        var result = assertInstanceOf(ResetResult.Failed.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals("SP refund failed", result.cause().getMessage());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(1, fixture.progress().activeNodes());
        assertEquals(2, fixture.progressRepository.saveCount);
    }

    @Test
    void resetReportsFailureToRestoreTreeAfterPointCreditFails() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));
        fixture.playerRepository.saveFailure = new IllegalStateException("SP refund failed");
        fixture.playerRepository.failSaveNumber = 1;
        fixture.progressRepository.saveFailure = new IllegalStateException("tree restore failed");
        fixture.progressRepository.failSaveNumber = 2;

        var result = assertInstanceOf(ResetResult.Failed.class,
                fixture.service.reset(PLAYER, PET_ID, true).join());

        assertEquals("SP refund failed", result.cause().getMessage());
        assertEquals(1, result.cause().getSuppressed().length);
        assertEquals("tree restore failed", result.cause().getSuppressed()[0].getMessage());
        assertEquals(0, fixture.progress().activeNodes());
    }

    @Test
    void resetAndConcurrentPointSpendShareOneSequence() {
        var fixture = new Fixture(alivePet(), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(1, 1, 0, 0));
        fixture.progressRepository.delayNextSave();

        var reset = fixture.service.reset(PLAYER, PET_ID, true);
        var spend = fixture.skillPointService.take(PLAYER, 3);

        assertFalse(reset.isDone());
        assertFalse(spend.isDone(), "SP mutation must wait for the complete tree operation");
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());

        fixture.progressRepository.failDelayedSave(new IllegalStateException("tree write failed"));

        assertInstanceOf(ResetResult.Failed.class, reset.join());
        assertEquals(new SkillPointResult.InsufficientSkillPoints(2, 3), spend.join());
        assertEquals(2, fixture.playerRepository.profile.skillPointBalance());
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
    void unlockReturnsWithoutWaitingForAnAsynchronousEconomyCheck() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));
        fixture.economy.balance = 100.0;
        fixture.economy.delayNextHas();

        var unlock = fixture.service.unlock(PLAYER, PET_ID, "health");
        var queuedUnlock = fixture.service.unlock(PLAYER, PET_ID, "health");

        assertFalse(unlock.isDone());
        assertFalse(queuedUnlock.isDone());
        assertEquals(List.of(), fixture.economy.withdrawals);
        fixture.economy.completeDelayedHas();

        assertEquals(1, assertInstanceOf(UnlockResult.Unlocked.class, unlock.join()).level());
        assertEquals(2, assertInstanceOf(UnlockResult.Unlocked.class, queuedUnlock.join()).level());
        assertEquals(List.of(10.0, 20.0), fixture.economy.withdrawals);
    }

    @Test
    void unlocksAFreeNodeWithoutCallingTheEconomy() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));

        var result = assertInstanceOf(UnlockResult.Unlocked.class,
                fixture.service.unlock(PLAYER, PET_ID, "free").join());

        assertEquals(BigDecimal.ZERO, result.moneyCost());
        assertEquals(List.of(), fixture.economy.withdrawals);
        assertEquals(List.of(), fixture.economy.deposits);
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

    @Test
    void requiresTheSkillPointServiceToUseTheSamePlayerQueue() {
        var fixture = new Fixture(alivePet(), 0, new RankLimits(1, 10, 10));

        assertThrows(IllegalArgumentException.class, () -> new SkillTreeService(
                fixture.petRepository,
                fixture.progressRepository,
                new InMemorySkillTreeRegistry(List.of(DEFINITION)),
                ignored -> Optional.of(TYPE_ID),
                fixture.economy,
                fixture.skillPointService,
                ignored -> new RankLimits(1, 10, 10),
                fixture.eventBus,
                new PlayerOperationQueue()));
    }

    @Test
    void deathResetUsesDurableRefundPortWithExactConfiguredCosts() {
        var fixture = new Fixture(pet(PLAYER, PetStatus.DEAD), 2, new RankLimits(1, 10, 10));
        fixture.progressRepository.progress = Optional.of(progress(2, 2, 1, 1));
        fixture.progressRepository.deathPlayer = fixture.playerRepository;

        var reset = assertInstanceOf(ResetResult.Reset.class,
                fixture.service.resetForDeath(PLAYER, PET_ID).join());
        assertEquals(4, reset.refundedSkillPoints());
        assertEquals(6, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(TreeProgress.empty(PET_ID, DEFINITION), fixture.progress());
        assertEquals(List.of(), fixture.economy.deposits);

        fixture.service.resetForDeath(PLAYER, PET_ID).join();
        assertEquals(6, fixture.playerRepository.profile.skillPointBalance());
        assertEquals(0, fixture.progressRepository.saveCount);
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
        private final PlayerOperationQueue operations = new PlayerOperationQueue();
        private final SkillPointService skillPointService;
        private final SkillTreeService service;

        private Fixture(OwnedPet pet, int skillPoints, RankLimits limits) {
            petRepository = new InMemoryPetRepository(pet);
            playerRepository = new InMemoryPlayerRepository(new PlayerProfile(PLAYER, skillPoints, List.of()));
            this.skillPointService = new SkillPointService(playerRepository, eventBus, operations);
            service = new SkillTreeService(
                    petRepository,
                    progressRepository,
                    new InMemorySkillTreeRegistry(List.of(DEFINITION)),
                    variantId -> variantId.equals(VARIANT_ID) ? Optional.of(TYPE_ID) : Optional.empty(),
                    economy,
                    skillPointService,
                    ignored -> limits,
                    eventBus,
                    operations);
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
        private InMemoryPlayerRepository deathPlayer;
        private boolean deathReset;

        @Override
        public CompletableFuture<Integer> resetForDeath(PlayerId owner, TreeProgress zeroed, int refund) {
            if (deathPlayer == null) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException());
            }
            if (!deathReset) {
                deathPlayer.profile = deathPlayer.profile.withSkillPointBalance(
                        Math.addExact(deathPlayer.profile.skillPointBalance(), refund));
                progress = Optional.of(zeroed);
                deathReset = true;
            }
            return CompletableFuture.completedFuture(deathPlayer.profile.skillPointBalance());
        }

        private RuntimeException loadFailure;
        private RuntimeException saveFailure;
        private int failSaveNumber;
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
            if (saveFailure != null && (failSaveNumber == 0 || saveCount == failSaveNumber)) {
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

        private void failDelayedSave(Throwable failure) {
            delayedSave.completeExceptionally(failure);
        }
    }

    private static final class InMemoryPlayerRepository implements PlayerRepository {
        private PlayerProfile profile;
        private RuntimeException saveFailure;
        private int failSaveNumber;
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
            saveCount++;
            if (saveFailure != null && (failSaveNumber == 0 || saveCount == failSaveNumber)) {
                return CompletableFuture.failedFuture(saveFailure);
            }
            this.profile = profile;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class FakeEconomy implements Economy {
        private double balance;
        private String balanceFailure;
        private String withdrawalFailure;
        private String depositFailure;
        private boolean delayNextHas;
        private CompletableFuture<EconomyResult> delayedHas;
        private boolean delayNextDeposit;
        private double delayedDepositAmount;
        private CompletableFuture<EconomyResult> delayedDeposit;
        private final List<Double> withdrawals = new ArrayList<>();
        private final List<Double> deposits = new ArrayList<>();

        @Override
        public CompletableFuture<EconomyResult> has(PlayerId playerId, double amount) {
            if (balanceFailure != null) {
                return CompletableFuture.completedFuture(new EconomyResult.Failure(balanceFailure));
            }
            if (delayNextHas) {
                delayNextHas = false;
                delayedHas = new CompletableFuture<>();
                return delayedHas;
            }
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount) {
            if (withdrawalFailure != null) {
                return CompletableFuture.completedFuture(new EconomyResult.Failure(withdrawalFailure));
            }
            balance -= amount;
            withdrawals.add(amount);
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount) {
            if (depositFailure != null) {
                return CompletableFuture.completedFuture(new EconomyResult.Failure(depositFailure));
            }
            if (delayNextDeposit) {
                delayNextDeposit = false;
                delayedDepositAmount = amount;
                delayedDeposit = new CompletableFuture<>();
                return delayedDeposit;
            }
            balance += amount;
            deposits.add(amount);
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        private void delayNextHas() {
            delayNextHas = true;
        }

        private void completeDelayedHas() {
            delayedHas.complete(new EconomyResult.Success(balance));
        }

        private void delayNextDeposit() {
            delayNextDeposit = true;
        }

        private void completeDelayedDeposit() {
            balance += delayedDepositAmount;
            deposits.add(delayedDepositAmount);
            delayedDeposit.complete(new EconomyResult.Success(balance));
        }
    }
}
