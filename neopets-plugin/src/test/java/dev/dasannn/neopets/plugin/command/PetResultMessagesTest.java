package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetStatus;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.DismissResult;
import dev.dasannn.neopets.core.pet.GiveResult;
import dev.dasannn.neopets.core.pet.SummonResult;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PetResultMessagesTest {
    private static final PetId PET_ID = new PetId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final VariantId VARIANT_ID = new VariantId("dog_pale");
    private static final OwnedPet PET = new OwnedPet(PET_ID,
            new PlayerId(UUID.fromString("00000000-0000-0000-0000-000000000002")), VARIANT_ID,
            Optional.empty(), PetStatus.ALIVE, false, Instant.EPOCH);

    @Test
    void mapsEveryGiveResult() {
        assertEquals(new MessageSpec("pets.given-admin", Map.of("pet", "Perro", "player", "Alex")),
                PetResultMessages.give(new GiveResult.Granted(PET), "Alex", ignored -> "Perro"));
        assertEquals(MessageSpec.of("pets.all-owned"), PetResultMessages.give(
                new GiveResult.AllOwned(), "Alex", ignored -> "Perro"));
        assertEquals(new MessageSpec("pets.unknown-variant", Map.of("variant", "dog_pale")),
                PetResultMessages.give(new GiveResult.UnknownVariant(VARIANT_ID), "Alex", ignored -> "Perro"));
        assertEquals(new MessageSpec("pets.already-owned", Map.of("player", "Alex")),
                PetResultMessages.give(new GiveResult.AlreadyOwned(VARIANT_ID), "Alex", ignored -> "Perro"));
    }

    @Test
    void mapsEverySummonResult() {
        assertEquals(pet("pets.summoned"), PetResultMessages.summon(
                new SummonResult.Summoned(PET), ignored -> "Perro"));
        assertEquals(MessageSpec.of("pets.not-owned"), PetResultMessages.summon(
                new SummonResult.NotOwned(PET_ID), ignored -> "Perro"));
        assertEquals(pet("pets.dead-unavailable"), PetResultMessages.summon(
                new SummonResult.Dead(PET), ignored -> "Perro"));
        assertEquals(pet("pets.in-transit"), PetResultMessages.summon(
                new SummonResult.InTransit(PET), ignored -> "Perro"));
        assertEquals(new MessageSpec("pets.active-limit", Map.of("limit", "1")),
                PetResultMessages.summon(new SummonResult.LimitReached(1), ignored -> "Perro"));
        assertEquals(pet("pets.already-active"), PetResultMessages.summon(
                new SummonResult.AlreadyActive(PET), ignored -> "Perro"));
    }

    @Test
    void mapsEveryDismissResult() {
        assertEquals(pet("pets.dismissed"), PetResultMessages.dismiss(
                new DismissResult.Dismissed(PET), ignored -> "Perro"));
        assertEquals(MessageSpec.of("pets.not-owned"), PetResultMessages.dismiss(
                new DismissResult.NotOwned(PET_ID), ignored -> "Perro"));
        assertEquals(pet("pets.dead-unavailable"), PetResultMessages.dismiss(
                new DismissResult.Dead(PET), ignored -> "Perro"));
        assertEquals(pet("pets.in-transit"), PetResultMessages.dismiss(
                new DismissResult.InTransit(PET), ignored -> "Perro"));
        assertEquals(pet("pets.already-inactive"), PetResultMessages.dismiss(
                new DismissResult.AlreadyInactive(PET), ignored -> "Perro"));
    }

    private static MessageSpec pet(String key) {
        return new MessageSpec(key, Map.of("pet", "Perro"));
    }
}
