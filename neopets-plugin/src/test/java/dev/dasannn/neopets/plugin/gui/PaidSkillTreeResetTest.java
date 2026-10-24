package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.port.Economy;
import dev.dasannn.neopets.core.port.EconomyResult;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PaidSkillTreeResetTest {
    private static final PlayerId PLAYER = new PlayerId(UUID.randomUUID());
    private static final PetId PET = new PetId(UUID.randomUUID());

    @Test
    void insufficientFundsDoNotInvokeResetOrWithdrawal() {
        var economy = new FakeEconomy(499);
        var resets = new AtomicInteger();
        var paidReset = new PaidSkillTreeReset(economy, (player, pet) -> {
            resets.incrementAndGet();
            return CompletableFuture.completedFuture(new ResetResult.NotOwned(pet));
        });

        var result = paidReset.reset(PLAYER, PET, BigDecimal.valueOf(500)).join();

        assertInstanceOf(PaidResetResult.InsufficientFunds.class, result);
        assertEquals(0, resets.get());
        assertEquals(0, economy.withdrawals);
    }

    @Test
    void successfulResetKeepsPaymentAndZeroCostSkipsEconomy() {
        var economy = new FakeEconomy(1_000);
        var paidReset = new PaidSkillTreeReset(economy, (player, pet) ->
                CompletableFuture.completedFuture(new ResetResult.Reset(
                        TreeProgress.empty(pet, new SkillTreeDefinition(
                                new PetTypeId("dog"), List.of())), 0)));

        PaidResetResult paid = paidReset.reset(PLAYER, PET, BigDecimal.valueOf(500)).join();
        PaidResetResult free = paidReset.reset(PLAYER, PET, BigDecimal.ZERO).join();

        assertInstanceOf(PaidResetResult.Completed.class, paid);
        assertInstanceOf(PaidResetResult.Completed.class, free);
        assertEquals(1, economy.withdrawals);
        assertEquals(0, economy.deposits);
        assertEquals(500, economy.balance);
    }

    @Test
    void failedCoreResultRefundsPayment() {
        var economy = new FakeEconomy(1_000);
        var paidReset = new PaidSkillTreeReset(economy, (player, pet) ->
                CompletableFuture.completedFuture(new ResetResult.Failed(new IllegalStateException("write"))));

        var result = paidReset.reset(PLAYER, PET, BigDecimal.valueOf(500)).join();

        assertInstanceOf(PaidResetResult.Completed.class, result);
        assertEquals(1, economy.withdrawals);
        assertEquals(1, economy.deposits);
        assertEquals(1_000, economy.balance);
    }

    @Test
    void synchronousResetFailureAfterWithdrawalRefundsPayment() {
        var economy = new FakeEconomy(1_000);
        var failure = new IllegalStateException("queue rejected");
        var paidReset = new PaidSkillTreeReset(economy, (player, pet) -> {
            throw failure;
        });

        var result = assertInstanceOf(PaidResetResult.Failed.class,
                paidReset.reset(PLAYER, PET, BigDecimal.valueOf(500)).join());

        assertSame(failure, result.cause());
        assertEquals(1, economy.withdrawals);
        assertEquals(1, economy.deposits);
        assertEquals(1_000, economy.balance);
    }

    @Test
    void exceptionalResetFutureAfterWithdrawalRefundsPayment() {
        var economy = new FakeEconomy(1_000);
        var failure = new IllegalStateException("write failed");
        var paidReset = new PaidSkillTreeReset(economy, (player, pet) ->
                CompletableFuture.failedFuture(failure));

        var result = assertInstanceOf(PaidResetResult.Failed.class,
                paidReset.reset(PLAYER, PET, BigDecimal.valueOf(500)).join());

        assertSame(failure, result.cause());
        assertEquals(1, economy.withdrawals);
        assertEquals(1, economy.deposits);
        assertEquals(1_000, economy.balance);
    }

    private static final class FakeEconomy implements Economy {
        private double balance;
        private int withdrawals;
        private int deposits;

        private FakeEconomy(double balance) {
            this.balance = balance;
        }

        @Override
        public CompletableFuture<EconomyResult> has(PlayerId playerId, double amount) {
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> withdraw(PlayerId playerId, double amount) {
            withdrawals++;
            balance -= amount;
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }

        @Override
        public CompletableFuture<EconomyResult> deposit(PlayerId playerId, double amount) {
            deposits++;
            balance += amount;
            return CompletableFuture.completedFuture(new EconomyResult.Success(balance));
        }
    }
}
