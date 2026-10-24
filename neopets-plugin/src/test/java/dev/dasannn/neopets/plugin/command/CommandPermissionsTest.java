package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CommandPermissionsTest {
    @Test void consolesAllowedRegardlessOfNodesAndOpNeverBypassesNodes() {
        assertTrue(CommandPermissions.has(sender(ConsoleCommandSender.class, false, false), "neopets.admin.revive"));
        assertTrue(CommandPermissions.has(sender(RemoteConsoleCommandSender.class, false, false), "neopets.admin.update"));
        assertFalse(CommandPermissions.has(sender(Player.class, true, false), "neopets.admin.update"));
        assertTrue(CommandPermissions.has(sender(Player.class, false, true), "neopets.admin.update"));
    }

    private CommandSender sender(Class<? extends CommandSender> type, boolean op, boolean permission) {
        return (CommandSender) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOp" -> op;
                    case "hasPermission" -> permission;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
