package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.*;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.GiveResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GiveCompensationTest {
    @Test
    void dispatchesExactlyOnceForBothAllOwnedFormsAndReplacesPlaceholders() {
        UUID uuid = UUID.randomUUID();
        for (GiveResult result : List.of(new GiveResult.AllOwned(), new GiveResult.AlreadyOwned(new VariantId("dog_pale")))) {
            var calls = new ArrayList<String>();
            GiveCompensation.run(result, "reward <player> <uuid>", "Ana", uuid, calls::add);
            assertEquals(List.of("reward Ana " + uuid), calls);
        }
    }

    @Test
    void disabledUnknownAndGrantedDoNotDispatch() {
        UUID uuid = UUID.randomUUID();
        var calls = new ArrayList<String>();
        for (String command : List.of("", "   ")) {
            GiveCompensation.run(new GiveResult.AllOwned(), command, "Ana", uuid, calls::add);
        }
        GiveCompensation.run(new GiveResult.UnknownVariant(new VariantId("unknown")), "reward Ana", "Ana", uuid, calls::add);
        GiveCompensation.run(new GiveResult.Granted(new OwnedPet(new PetId(UUID.randomUUID()), new PlayerId(uuid),
                new VariantId("dog_pale"), Optional.empty(), PetStatus.ALIVE, false, Instant.EPOCH)),
                "reward Ana", "Ana", uuid, calls::add);
        assertTrue(calls.isEmpty());
    }
}
