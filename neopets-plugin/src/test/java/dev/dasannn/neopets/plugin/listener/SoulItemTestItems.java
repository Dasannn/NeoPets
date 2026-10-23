package dev.dasannn.neopets.plugin.listener;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Minimal immutable stacks for exercising PDC event guards without a running server. */
final class SoulItemTestItems {
    private SoulItemTestItems() {
    }

    static ItemStack item(Map<NamespacedKey, Object> values) {
        var data = proxy(PersistentDataContainer.class, (proxy, method, args) -> switch (method.getName()) {
            case "has" -> values.containsKey(args[0]) && (args.length == 1
                    || ((PersistentDataType<?, ?>) args[1]).getPrimitiveType().isInstance(values.get(args[0])));
            case "get" -> {
                Object value = values.get(args[0]);
                if (value != null && !((PersistentDataType<?, ?>) args[1]).getPrimitiveType().isInstance(value)) {
                    throw new IllegalArgumentException("Wrong PDC primitive type");
                }
                yield value;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var meta = proxy(ItemMeta.class, (proxy, method, args) -> switch (method.getName()) {
            case "getPersistentDataContainer" -> data;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return new ItemStack() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Material getType() {
                return Material.ECHO_SHARD;
            }

            @Override
            public boolean hasItemMeta() {
                return true;
            }

            @Override
            public ItemMeta getItemMeta() {
                return meta;
            }

            @Override
            public ItemStack clone() {
                return this;
            }
        };
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
