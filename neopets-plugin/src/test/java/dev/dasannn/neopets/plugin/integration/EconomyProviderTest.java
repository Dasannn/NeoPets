package dev.dasannn.neopets.plugin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.port.Economy;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class EconomyProviderTest {
    @Test
    void fallsBackAndWarnsOnceWhenVaultIsMissing() {
        var log = new RecordingLog();
        Server server = server(false, servicesManager());

        Economy economy = EconomyProvider.resolve(server, Runnable::run, log.logger);

        assertInstanceOf(DisabledEconomy.class, economy);
        assertEquals(1, log.records.size());
        assertEquals(Level.WARNING, log.records.getFirst().getLevel());
        assertTrue(log.records.getFirst().getMessage().contains("Vault is not enabled"));
    }

    @Test
    void fallsBackAndWarnsOnceWhenNoEconomyServiceIsRegistered() {
        var log = new RecordingLog();
        Server server = server(true, servicesManager());

        Economy economy = EconomyProvider.resolve(server, Runnable::run, log.logger);

        assertInstanceOf(DisabledEconomy.class, economy);
        assertEquals(1, log.records.size());
        assertTrue(log.records.getFirst().getMessage().contains("no registered economy provider"));
    }

    private static Server server(boolean vaultEnabled, ServicesManager servicesManager) {
        PluginManager pluginManager = proxy(PluginManager.class, (methodName, arguments) -> {
            if (methodName.equals("isPluginEnabled")) {
                return vaultEnabled;
            }
            throw new UnsupportedOperationException(methodName);
        });
        return proxy(Server.class, (methodName, arguments) -> switch (methodName) {
            case "getPluginManager" -> pluginManager;
            case "getServicesManager" -> servicesManager;
            default -> throw new UnsupportedOperationException(methodName);
        });
    }

    private static ServicesManager servicesManager() {
        return proxy(ServicesManager.class, (methodName, arguments) -> {
            if (methodName.equals("getRegistration")) {
                return null;
            }
            throw new UnsupportedOperationException(methodName);
        });
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        Object proxy = Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (ignored, method, arguments) -> invocation.invoke(method.getName(), arguments));
        return type.cast(proxy);
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String methodName, Object[] arguments);
    }

    private static final class RecordingLog {
        private final List<LogRecord> records = new ArrayList<>();
        private final Logger logger = Logger.getAnonymousLogger();

        private RecordingLog() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override
                public void publish(LogRecord record) {
                    records.add(record);
                }

                @Override
                public void flush() {
                }

                @Override
                public void close() {
                }
            });
        }
    }
}
