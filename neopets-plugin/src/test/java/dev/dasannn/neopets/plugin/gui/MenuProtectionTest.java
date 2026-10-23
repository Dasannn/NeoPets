package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

class MenuProtectionTest {
    @Test
    void actualClickHandlerCancelsEveryClickTypeAndDispatchesItsExactType() {
        var harness = new MenuHarness();
        var dispatched = new ArrayList<ClickType>();
        harness.menu.open(harness.player, actionLayout(),
                (session, player, action, click) -> dispatched.add(click));

        for (ClickType clickType : ClickType.values()) {
            var event = harness.click(0, clickType);
            harness.menu.onClick(event);
            assertTrue(event.isCancelled(), clickType.name());
        }

        assertEquals(List.of(ClickType.values()), dispatched);
    }

    @Test
    void actualHandlersProtectPlayerInventoryAndDragButIgnoreOrdinaryViews() {
        var harness = new MenuHarness();
        harness.menu.open(harness.player, actionLayout(), (session, player, action, click) -> { });

        var bottomClick = harness.click(12, ClickType.SHIFT_LEFT);
        harness.menu.onClick(bottomClick);
        var drag = new InventoryDragEvent(harness.view(), null, null, false, Map.of());
        harness.menu.onDrag(drag);

        assertTrue(bottomClick.isCancelled());
        assertTrue(drag.isCancelled());

        harness.holder.set(null);
        var ordinaryClick = harness.click(0, ClickType.NUMBER_KEY);
        harness.menu.onClick(ordinaryClick);
        var ordinaryDrag = new InventoryDragEvent(harness.view(), null, null, true, Map.of());
        harness.menu.onDrag(ordinaryDrag);

        assertFalse(ordinaryClick.isCancelled());
        assertFalse(ordinaryDrag.isCancelled());
    }

    @Test
    void closeActionChangesTheViewOnTheNextTick() {
        var harness = new MenuHarness();
        harness.menu.open(harness.player, actionLayout(),
                (session, player, action, click) -> session.close());

        harness.menu.onClick(harness.click(0, ClickType.LEFT));

        assertEquals(0, harness.closed.get());
        assertEquals(1, harness.nextTick.size());
        harness.nextTick.removeFirst().run();
        assertEquals(1, harness.closed.get());
    }

    private static MenuLayout actionLayout() {
        return new MenuLayout("Test", 1, Map.of(0,
                MenuItem.action("STONE", "Action", List.of(), false, "action")));
    }

    private static final class MenuHarness {
        private final AtomicReference<InventoryHolder> holder = new AtomicReference<>();
        private final AtomicReference<InventoryView> view = new AtomicReference<>();
        private final List<Runnable> nextTick = new ArrayList<>();
        private final AtomicInteger closed = new AtomicInteger();
        private final Inventory inventory = proxy(Inventory.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getSize" -> 9;
            case "getHolder" -> holder.get();
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
        private final Player player = proxy(Player.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "openInventory" -> view.get();
            case "getOpenInventory" -> view.get();
            case "closeInventory" -> {
                closed.incrementAndGet();
                yield null;
            }
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
        private final Server server = proxy(Server.class, (proxy, method, arguments) -> {
            if (method.getName().equals("createInventory")) {
                holder.set((InventoryHolder) arguments[0]);
                return inventory;
            }
            return defaultValue(method.getReturnType());
        });
        private final Menu menu = new Menu(server, Logger.getAnonymousLogger(), nextTick::add, ignored -> null);

        private MenuHarness() {
            view.set(proxy(InventoryView.class, (proxy, method, arguments) -> switch (method.getName()) {
                case "getTopInventory", "getBottomInventory", "getInventory" -> inventory;
                case "getPlayer" -> player;
                case "convertSlot" -> arguments[0];
                case "countSlots" -> 18;
                case "getType" -> InventoryType.CHEST;
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> defaultValue(method.getReturnType());
            }));
        }

        private InventoryView view() {
            return view.get();
        }

        private InventoryClickEvent click(int rawSlot, ClickType clickType) {
            return new InventoryClickEvent(view(), InventoryType.SlotType.CONTAINER, rawSlot,
                    clickType, InventoryAction.NOTHING);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> false;
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0.0F;
            case "double" -> 0.0D;
            case "char" -> '\0';
            default -> null;
        };
    }
}
