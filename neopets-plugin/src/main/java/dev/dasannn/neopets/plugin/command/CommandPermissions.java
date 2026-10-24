package dev.dasannn.neopets.plugin.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

/** Shared command and completion authorization; OP status confers no extra access. */
public final class CommandPermissions {
    private CommandPermissions() { }

    /** Grants consoles admin access, otherwise requires an explicit Bukkit permission. */
    public static boolean has(CommandSender sender, String permission) {
        return (permission.startsWith("neopets.admin")
                && (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender))
                || sender.hasPermission(permission);
    }
}
