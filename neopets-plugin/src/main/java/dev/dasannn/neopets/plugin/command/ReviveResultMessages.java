package dev.dasannn.neopets.plugin.command;

import java.util.Map;

/** Localized message mapping for every revive-command outcome. */
public final class ReviveResultMessages {
    private ReviveResultMessages() {
    }

    /** Maps an outcome to the command sender's message. */
    public static MessageSpec sender(ReviveCommandResult result, String playerName, String petName) {
        return switch (result) {
            case ReviveCommandResult.Revived ignored -> new MessageSpec(
                    "pets.revived-admin", Map.of("pet", petName, "player", playerName));
            case ReviveCommandResult.PlayerOffline offline -> new MessageSpec(
                    "errors.player-offline", Map.of("player", offline.playerName()));
            case ReviveCommandResult.NoSoulItem ignored -> MessageSpec.of("pets.soul-required");
            case ReviveCommandResult.InvalidSoul ignored -> MessageSpec.of("pets.soul-invalid");
            case ReviveCommandResult.WrongOwner ignored -> MessageSpec.of("pets.soul-wrong-owner");
            case ReviveCommandResult.PetNotDead ignored -> MessageSpec.of("pets.revive-not-dead");
        };
    }
}
