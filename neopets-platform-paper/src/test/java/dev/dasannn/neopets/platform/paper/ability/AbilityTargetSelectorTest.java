package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbilityTargetSelectorTest {
    @Test
    void selectsOnlyHostilesAndExcludesOwnerPlayersAndPets() {
        UUID ownerId = UUID.randomUUID();
        var candidates = List.of(
                candidate("hostile", UUID.randomUUID(), true, false, false),
                candidate("passive", UUID.randomUUID(), false, false, false),
                candidate("player", UUID.randomUUID(), true, true, false),
                candidate("pet", UUID.randomUUID(), true, false, true),
                candidate("owner", ownerId, true, false, false));

        assertEquals(List.of("hostile"), AbilityTargetSelector.selectHostiles(candidates, ownerId));
    }

    private static AbilityTargetSelector.TargetCandidate<String> candidate(
            String target,
            UUID entityId,
            boolean hostile,
            boolean player,
            boolean pet
    ) {
        return new AbilityTargetSelector.TargetCandidate<>(target, entityId, hostile, player, pet);
    }
}
