package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.config.AdminConfigurationService;
import dev.dasannn.neopets.plugin.config.ConfigurationLoader;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminMenuDispatchTest {
    @TempDir
    Path directory;

    @Test
    void editsReloadBeforeRefreshingOnMainThreadAndRecheckPermission() throws Exception {
        var loader = new ConfigurationLoader(directory);
        var configuration = new ConfigurationManager(loader);
        var mainTasks = new LinkedBlockingQueue<Runnable>();
        var permitted = new AtomicBoolean(true);
        var sent = new ArrayList<String>();
        Player player = proxy(Player.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "hasPermission" -> permitted.get();
            case "isOnline" -> true;
            case "sendMessage" -> {
                for (Object argument : arguments) {
                    if (argument instanceof Component component) {
                        sent.add(PlainTextComponentSerializer.plainText().serialize(component));
                    }
                }
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (ignored, method, arguments) -> {
            if (method.getName().equals("runTask")) {
                mainTasks.add((Runnable) arguments[1]);
            }
            return defaultValue(method.getReturnType());
        });
        Server server = proxy(Server.class, (ignored, method, arguments) ->
                method.getName().equals("getScheduler") ? scheduler : defaultValue(method.getReturnType()));
        Plugin plugin = proxy(Plugin.class, (ignored, method, arguments) ->
                method.getName().equals("getServer") ? server : defaultValue(method.getReturnType()));
        var logger = Logger.getAnonymousLogger();
        var framework = new Menu(server, logger, mainTasks::add, ignored -> null);
        var session = new RecordingSession();
        var state = new AdminMenu.ViewState();

        try (var service = new AdminConfigurationService(directory, loader, configuration)) {
            var menu = new AdminMenu(plugin, framework, configuration, service,
                    new LocalizedMessages(configuration), logger);
            menu.handle(session, player, state, "admin:feature:death.enabled", ClickType.LEFT);
            // A second click while an edit is pending must not queue another toggle.
            menu.handle(session, player, state, "admin:feature:death.enabled", ClickType.LEFT);
            Runnable completion = mainTasks.poll(5, TimeUnit.SECONDS);
            assertNotNull(completion);
            assertFalse(configuration.current().settings().features().death().enabled());
            assertEquals(0, session.refreshes);
            assertTrue(sent.isEmpty());
            completion.run();
            assertEquals(1, session.refreshes);
            assertTrue(sent.getLast().contains("desactivada"));

            menu.handle(session, player, state, "admin:feature:reset.cost", ClickType.SHIFT_RIGHT);
            completion = mainTasks.poll(5, TimeUnit.SECONDS);
            assertNotNull(completion);
            completion.run();
            assertEquals(4_000, configuration.current().settings().features().reset().cost().intValueExact());
            assertEquals(2, session.refreshes);

            permitted.set(false);
            menu.handle(session, player, state, "admin:feature:death.enabled", ClickType.RIGHT);
            assertTrue(session.closed);
            assertFalse(configuration.current().settings().features().death().enabled());
            assertTrue(mainTasks.isEmpty());
            menu.open(player);
            assertTrue(sent.getLast().contains("permiso"));
        }
    }

    private static final class RecordingSession implements Menu.Session {
        private final Thread main = Thread.currentThread();
        private int refreshes;
        private boolean closed;

        @Override
        public void refresh(MenuLayout layout) {
            assertEquals(main, Thread.currentThread());
            refreshes++;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        return type == boolean.class ? false : type.isPrimitive() ? 0 : null;
    }
}
