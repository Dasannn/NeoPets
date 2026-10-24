package dev.dasannn.neopets.plugin.integration;

import dev.dasannn.neopets.core.port.Economy;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Resolves NeoPets' economy implementation once during plugin enablement. */
public final class EconomyProvider {
    private EconomyProvider() {
    }

    /**
     * Resolves the registered Vault provider, or returns a disabled fallback.
     *
     * @param server Bukkit server whose services are queried
     * @param mainThreadDispatcher dispatcher used for every subsequent Vault call
     * @param logger plugin logger used for the single fallback warning
     * @return an available Vault adapter or a disabled economy
     */
    public static Economy resolve(
            Server server,
            Function<Runnable, CompletableFuture<Void>> mainThreadDispatcher,
            Logger logger
    ) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(mainThreadDispatcher, "mainThreadDispatcher");
        Objects.requireNonNull(logger, "logger");

        if (!server.getPluginManager().isPluginEnabled("Vault")) {
            return unavailable(logger, "Vault is not enabled");
        }

        RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> registration =
                server.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return unavailable(logger, "Vault has no registered economy provider");
        }

        return new VaultEconomy(
                registration.getProvider(),
                mainThreadDispatcher,
                playerId -> server.getOfflinePlayer(playerId.value()));
    }

    private static Economy unavailable(Logger logger, String detail) {
        logger.warning(detail + "; economy-backed NeoPets features are disabled");
        return new DisabledEconomy();
    }
}
