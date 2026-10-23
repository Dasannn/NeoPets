package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.EconomyResult;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;

class VaultEconomyTest {
    private static final PlayerId PLAYER_ID = new PlayerId(
            UUID.fromString("a35e168b-1c24-4f26-88dd-44f37651da27"));
    private static final OfflinePlayer OFFLINE_PLAYER = offlinePlayer();

    @Test
    void dispatchesChecksAndMapsSuccessfulTransactions() {
        var vault = new FakeVaultEconomy();
        vault.hasResult = true;
        vault.withdrawResponse = response(2.50, 17.50, EconomyResponse.ResponseType.SUCCESS, null);
        vault.depositResponse = response(4.25, 21.75, EconomyResponse.ResponseType.SUCCESS, null);
        var executor = new DirectExecutor();
        var economy = new VaultEconomy(vault, executor, ignored -> OFFLINE_PLAYER);

        boolean hasFunds = economy.has(PLAYER_ID, 2.50);
        EconomyResult withdrawal = economy.withdraw(PLAYER_ID, 2.50);
        EconomyResult deposit = economy.deposit(PLAYER_ID, 4.25);

        assertEquals(true, hasFunds);
        assertEquals(3, executor.executions);
        assertEquals(OFFLINE_PLAYER, vault.lastPlayer);
        assertEquals(4.25, vault.lastAmount);
        assertEquals(17.50, assertInstanceOf(EconomyResult.Success.class, withdrawal).balance());
        assertEquals(21.75, assertInstanceOf(EconomyResult.Success.class, deposit).balance());
    }

    @Test
    void mapsVaultBusinessFailuresWithoutThrowing() {
        var vault = new FakeVaultEconomy();
        vault.withdrawResponse = response(
                10.0, 3.0, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        vault.depositResponse = response(
                10.0, 3.0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "");
        var economy = new VaultEconomy(vault, Runnable::run, ignored -> OFFLINE_PLAYER);

        EconomyResult withdrawal = economy.withdraw(PLAYER_ID, 10.0);
        EconomyResult deposit = economy.deposit(PLAYER_ID, 10.0);

        assertEquals(
                "Insufficient funds",
                assertInstanceOf(EconomyResult.Failure.class, withdrawal).reason());
        assertEquals(
                "Economy operation is not supported",
                assertInstanceOf(EconomyResult.Failure.class, deposit).reason());
    }

    @Test
    void handlesMalformedVaultResponsesAsFailures() {
        var vault = new FakeVaultEconomy();
        vault.withdrawResponse = null;
        vault.depositResponse = response(
                5.0, Double.NaN, EconomyResponse.ResponseType.SUCCESS, null);
        var economy = new VaultEconomy(vault, Runnable::run, ignored -> OFFLINE_PLAYER);

        EconomyResult withdrawal = economy.withdraw(PLAYER_ID, 5.0);
        EconomyResult deposit = economy.deposit(PLAYER_ID, 5.0);

        assertEquals(
                "Economy provider returned no response",
                assertInstanceOf(EconomyResult.Failure.class, withdrawal).reason());
        assertEquals(
                "Economy provider returned an invalid balance",
                assertInstanceOf(EconomyResult.Failure.class, deposit).reason());
    }

    @Test
    void rejectsNonPositiveNonFiniteAndOverPrecisionAmounts() {
        var vault = new FakeVaultEconomy();
        vault.fractionalDigits = 2;
        var economy = new VaultEconomy(vault, Runnable::run, ignored -> OFFLINE_PLAYER);

        assertThrows(IllegalArgumentException.class, () -> economy.has(PLAYER_ID, 0.0));
        assertThrows(IllegalArgumentException.class, () -> economy.withdraw(PLAYER_ID, -1.0));
        assertThrows(IllegalArgumentException.class, () -> economy.deposit(PLAYER_ID, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> economy.deposit(PLAYER_ID, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> economy.withdraw(PLAYER_ID, 1.001));
        assertEquals(0, vault.transactionCalls);
    }

    @Test
    void acceptsAnyScaleWhenProviderDoesNotRound() {
        var vault = new FakeVaultEconomy();
        vault.fractionalDigits = -1;
        vault.depositResponse = response(
                0.0001, 1.0001, EconomyResponse.ResponseType.SUCCESS, null);
        var economy = new VaultEconomy(vault, Runnable::run, ignored -> OFFLINE_PLAYER);

        EconomyResult result = economy.deposit(PLAYER_ID, 0.0001);

        assertEquals(1.0001, assertInstanceOf(EconomyResult.Success.class, result).balance());
    }

    @Test
    void disabledEconomyReturnsUnavailableOutcomes() {
        var economy = new DisabledEconomy();

        assertFalse(economy.has(PLAYER_ID, 1.0));
        assertEquals(
                DisabledEconomy.UNAVAILABLE_REASON,
                assertInstanceOf(EconomyResult.Failure.class, economy.withdraw(PLAYER_ID, 1.0)).reason());
        assertEquals(
                DisabledEconomy.UNAVAILABLE_REASON,
                assertInstanceOf(EconomyResult.Failure.class, economy.deposit(PLAYER_ID, 1.0)).reason());
    }

    private static EconomyResponse response(
            double amount,
            double balance,
            EconomyResponse.ResponseType type,
            String errorMessage
    ) {
        return new EconomyResponse(amount, balance, type, errorMessage);
    }

    private static OfflinePlayer offlinePlayer() {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> PLAYER_ID.value();
                    case "getName" -> "TestPlayer";
                    case "toString" -> "TestPlayer (" + PLAYER_ID + ")";
                    case "hashCode" -> PLAYER_ID.hashCode();
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class DirectExecutor implements Executor {
        private int executions;

        @Override
        public void execute(Runnable command) {
            executions++;
            command.run();
        }
    }

    @SuppressWarnings("deprecation")
    private static final class FakeVaultEconomy extends AbstractEconomy {
        private int fractionalDigits = 2;
        private boolean hasResult;
        private EconomyResponse withdrawResponse;
        private EconomyResponse depositResponse;
        private OfflinePlayer lastPlayer;
        private double lastAmount;
        private int transactionCalls;

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public String getName() {
            return "Fake";
        }

        @Override
        public boolean hasBankSupport() {
            return false;
        }

        @Override
        public int fractionalDigits() {
            return fractionalDigits;
        }

        @Override
        public String format(double amount) {
            return Double.toString(amount);
        }

        @Override
        public String currencyNamePlural() {
            return "coins";
        }

        @Override
        public String currencyNameSingular() {
            return "coin";
        }

        @Override
        public boolean hasAccount(String playerName) {
            return true;
        }

        @Override
        public boolean hasAccount(String playerName, String worldName) {
            return true;
        }

        @Override
        public double getBalance(String playerName) {
            return 0;
        }

        @Override
        public double getBalance(String playerName, String world) {
            return 0;
        }

        @Override
        public boolean has(String playerName, double amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean has(String playerName, String worldName, double amount) {
            return has(playerName, amount);
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, double amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
            return withdrawPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, double amount) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
            return depositPlayer(playerName, amount);
        }

        @Override
        public EconomyResponse createBank(String name, String player) {
            return unsupported();
        }

        @Override
        public EconomyResponse deleteBank(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankBalance(String name) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankHas(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankWithdraw(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse bankDeposit(String name, double amount) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankOwner(String name, String playerName) {
            return unsupported();
        }

        @Override
        public EconomyResponse isBankMember(String name, String playerName) {
            return unsupported();
        }

        @Override
        public List<String> getBanks() {
            return List.of();
        }

        @Override
        public boolean createPlayerAccount(String playerName) {
            return true;
        }

        @Override
        public boolean createPlayerAccount(String playerName, String worldName) {
            return true;
        }

        @Override
        public boolean has(OfflinePlayer player, double amount) {
            recordCall(player, amount);
            return hasResult;
        }

        @Override
        public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
            recordCall(player, amount);
            return withdrawResponse;
        }

        @Override
        public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
            recordCall(player, amount);
            return depositResponse;
        }

        private void recordCall(OfflinePlayer player, double amount) {
            transactionCalls++;
            lastPlayer = player;
            lastAmount = amount;
        }

        private static EconomyResponse unsupported() {
            return response(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Not supported");
        }
    }
}
