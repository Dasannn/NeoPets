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
        assertEquals("pets.given-admin", PetResultMessages.give(
                new GiveResult.Granted(PET), "Alex", ignored -> "Perro").key());
        assertEquals("pets.all-owned", PetResultMessages.give(
                new GiveResult.AllOwned(), "Alex", ignored -> "Perro").key());
        assertEquals("pets.unknown-variant", PetResultMessages.give(
                new GiveResult.UnknownVariant(VARIANT_ID), "Alex", ignored -> "Perro").key());
        assertEquals("pets.already-owned", PetResultMessages.give(
                new GiveResult.AlreadyOwned(VARIANT_ID), "Alex", ignored -> "Perro").key());
    }

    @Test
    void mapsEverySummonResult() {
        assertEquals("pets.summoned", PetResultMessages.summon(
                new SummonResult.Summoned(PET), ignored -> "Perro").key());
        assertEquals("pets.not-owned", PetResultMessages.summon(
                new SummonResult.NotOwned(PET_ID), ignored -> "Perro").key());
        assertEquals("pets.dead-unavailable", PetResultMessages.summon(
                new SummonResult.Dead(PET), ignored -> "Perro").key());
        assertEquals("pets.in-transit", PetResultMessages.summon(
                new SummonResult.InTransit(PET), ignored -> "Perro").key());
        assertEquals("pets.active-limit", PetResultMessages.summon(
                new SummonResult.LimitReached(1), ignored -> "Perro").key());
        assertEquals("pets.already-active", PetResultMessages.summon(
                new SummonResult.AlreadyActive(PET), ignored -> "Perro").key());
    }

    @Test
    void mapsEveryDismissResult() {
        assertEquals("pets.dismissed", PetResultMessages.dismiss(
                new DismissResult.Dismissed(PET), ignored -> "Perro").key());
        assertEquals("pets.not-owned", PetResultMessages.dismiss(
                new DismissResult.NotOwned(PET_ID), ignored -> "Perro").key());
        assertEquals("pets.dead-unavailable", PetResultMessages.dismiss(
                new DismissResult.Dead(PET), ignored -> "Perro").key());
        assertEquals("pets.in-transit", PetResultMessages.dismiss(
                new DismissResult.InTransit(PET), ignored -> "Perro").key());
        assertEquals("pets.already-inactive", PetResultMessages.dismiss(
                new DismissResult.AlreadyInactive(PET), ignored -> "Perro").key());
    }
}
