package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.pet.GiveResult;
import java.util.UUID;
import java.util.function.Consumer;

/** Dispatches the optional console reward after an entirely owned give selection. */
public final class GiveCompensation {
    private GiveCompensation() { }

    /** Dispatches once for all-owned results (including a specific owned variant). */
    public static void run(GiveResult result, String command, String player, UUID uuid, Consumer<String> console) {
        if ((result instanceof GiveResult.AllOwned || result instanceof GiveResult.AlreadyOwned)
                && !command.isBlank()) {
            console.accept(command.replace("<player>", player).replace("<uuid>", uuid.toString()));
        }
    }
}
