package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.ReviveResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviveResultMessagesTest {
    private static final PetId PET_ID = new PetId(new UUID(1, 1));
    private static final OwnedPet PET = new OwnedPet(PET_ID, new PlayerId(new UUID(2, 2)),
            new VariantId("dog_pale"), Optional.empty(), PetStatus.ALIVE, false, Instant.EPOCH);

    @Test
    void mapsEveryCoreOutcomeToASealedCommandOutcome() {
        assertEquals(new ReviveCommandResult.Revived(PET),
                ReviveCommandResult.fromCore(new ReviveResult.Revived(PET)));
        assertEquals(new ReviveCommandResult.InvalidSoul(),
                ReviveCommandResult.fromCore(new ReviveResult.InvalidSoul(PET_ID)));
        assertEquals(new ReviveCommandResult.WrongOwner(),
                ReviveCommandResult.fromCore(new ReviveResult.NotOwned(PET_ID)));
        assertEquals(new ReviveCommandResult.PetNotDead(),
                ReviveCommandResult.fromCore(new ReviveResult.AlreadyAlive(PET)));
        assertEquals(new ReviveCommandResult.PetNotDead(),
                ReviveCommandResult.fromCore(new ReviveResult.InTransit(PET)));
    }

    @Test
    void mapsEveryCommandFailureAndSuccessToLanguageKeys() {
        assertEquals(new MessageSpec("pets.revived-admin", Map.of("pet", "Pipo", "player", "Alex")),
                ReviveResultMessages.sender(new ReviveCommandResult.Revived(PET), "Alex", "Pipo"));
        assertEquals(new MessageSpec("errors.player-offline", Map.of("player", "Alex")),
                ReviveResultMessages.sender(new ReviveCommandResult.PlayerOffline("Alex"), "ignored", ""));
        assertEquals(MessageSpec.of("pets.soul-required"),
                ReviveResultMessages.sender(new ReviveCommandResult.NoSoulItem(), "Alex", ""));
        assertEquals(MessageSpec.of("pets.soul-invalid"),
                ReviveResultMessages.sender(new ReviveCommandResult.InvalidSoul(), "Alex", ""));
        assertEquals(MessageSpec.of("pets.soul-wrong-owner"),
                ReviveResultMessages.sender(new ReviveCommandResult.WrongOwner(), "Alex", ""));
        assertEquals(MessageSpec.of("pets.revive-not-dead"),
                ReviveResultMessages.sender(new ReviveCommandResult.PetNotDead(), "Alex", ""));
    }
}
