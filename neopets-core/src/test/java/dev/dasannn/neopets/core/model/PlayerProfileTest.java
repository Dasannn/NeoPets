package dev.dasannn.neopets.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerProfileTest {
    private final PlayerId playerId = new PlayerId(UUID.randomUUID());

    @Test
    void requiresNonNegativeBalanceAndNonNullFields() {
        assertThrows(NullPointerException.class, () -> new PlayerProfile(null, 0, List.of()));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(playerId, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProfile(playerId, -1, List.of()));
    }

    @Test
    void requiresPetsToBelongToProfilePlayer() {
        var anotherOwner = new PlayerId(UUID.randomUUID());
        var foreignPet = pet(anotherOwner, "dog_brown", false);

        assertThrows(IllegalArgumentException.class,
                () -> new PlayerProfile(playerId, 0, List.of(foreignPet)));
    }

    @Test
    void rejectsDuplicatePetIdsAndVariants() {
        var first = pet(playerId, "dog_brown", false);
        var duplicateId = new OwnedPet(first.id(), playerId, new VariantId("dog_black"), Optional.empty(),
                PetStatus.ALIVE, false, first.createdAt());
        var duplicateVariant = pet(playerId, "dog_brown", true);

        assertThrows(IllegalArgumentException.class,
                () -> new PlayerProfile(playerId, 0, List.of(first, duplicateId)));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerProfile(playerId, 0, List.of(first, duplicateVariant)));
    }

    @Test
    void defensivelyCopiesPetsAndProvidesQueries() {
        var inactive = pet(playerId, "dog_brown", false);
        var active = pet(playerId, "cat_black", true);
        var source = new ArrayList<>(List.of(inactive, active));

        var profile = new PlayerProfile(playerId, 4, source);
        source.clear();

        assertEquals(2, profile.ownedPets().size());
        assertThrows(UnsupportedOperationException.class, () -> profile.ownedPets().clear());
        assertTrue(profile.ownsVariant(new VariantId("cat_black")));
        assertFalse(profile.ownsVariant(new VariantId("parrot_red")));
        assertThrows(NullPointerException.class, () -> profile.ownsVariant(null));
        assertEquals(List.of(active), profile.activePets());
    }

    @Test
    void withMethodsRetainUnchangedStateAndValidateChanges() {
        var pet = pet(playerId, "dog_brown", false);
        var profile = new PlayerProfile(playerId, 2, List.of(pet));

        assertEquals(5, profile.withSkillPointBalance(5).skillPointBalance());
        assertEquals(List.of(pet), profile.withSkillPointBalance(5).ownedPets());
        assertEquals(List.of(), profile.withOwnedPets(List.of()).ownedPets());
        assertEquals(2, profile.withOwnedPets(List.of()).skillPointBalance());
        assertThrows(IllegalArgumentException.class, () -> profile.withSkillPointBalance(-1));
    }

    @Test
    void rankLimitsMustBeNonNegative() {
        assertEquals(new RankLimits(0, 0, 0), new RankLimits(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RankLimits(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RankLimits(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new RankLimits(0, 0, -1));
    }

    private static OwnedPet pet(PlayerId owner, String variant, boolean active) {
        return new OwnedPet(new PetId(UUID.randomUUID()), owner, new VariantId(variant), Optional.empty(),
                PetStatus.ALIVE, active, Instant.parse("2026-10-23T10:15:30Z"));
    }
}
