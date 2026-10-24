package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.pet.DismissResult;
import dev.dasannn.neopets.core.pet.GiveResult;
import dev.dasannn.neopets.core.pet.SummonResult;
import java.util.Map;
import java.util.function.Function;

/** Exhaustive localized-message mapping for pet service outcomes. */
public final class PetResultMessages {
    private PetResultMessages() {
    }

    /** Maps every give result to an administrator-facing message. */
    public static MessageSpec give(
            GiveResult result, String playerName, Function<OwnedPet, String> petName
    ) {
        return switch (result) {
            case GiveResult.Granted granted -> new MessageSpec("pets.given-admin", Map.of(
                    "pet", petName.apply(granted.pet()), "player", playerName));
            case GiveResult.AllOwned ignored -> MessageSpec.of("pets.all-owned");
            case GiveResult.UnknownVariant unknown -> new MessageSpec(
                    "pets.unknown-variant", Map.of("variant", unknown.variantId().value()));
            case GiveResult.AlreadyOwned ignored -> new MessageSpec(
                    "pets.already-owned", Map.of("player", playerName));
        };
    }

    /** Maps every summon result to a player-facing message. */
    public static MessageSpec summon(SummonResult result, Function<OwnedPet, String> petName) {
        return switch (result) {
            case SummonResult.Summoned summoned -> pet("pets.summoned", summoned.pet(), petName);
            case SummonResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case SummonResult.Dead dead -> pet("pets.dead-unavailable", dead.pet(), petName);
            case SummonResult.InTransit transit -> pet("pets.in-transit", transit.pet(), petName);
            case SummonResult.LimitReached limit -> new MessageSpec(
                    "pets.active-limit", Map.of("limit", Integer.toString(limit.maxActivePets())));
            case SummonResult.AlreadyActive active -> pet("pets.already-active", active.pet(), petName);
        };
    }

    /** Maps every dismiss result to a player-facing message. */
    public static MessageSpec dismiss(DismissResult result, Function<OwnedPet, String> petName) {
        return switch (result) {
            case DismissResult.Dismissed dismissed -> pet("pets.dismissed", dismissed.pet(), petName);
            case DismissResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case DismissResult.Dead dead -> pet("pets.dead-unavailable", dead.pet(), petName);
            case DismissResult.InTransit transit -> pet("pets.in-transit", transit.pet(), petName);
            case DismissResult.AlreadyInactive inactive -> pet(
                    "pets.already-inactive", inactive.pet(), petName);
        };
    }

    private static MessageSpec pet(
            String key, OwnedPet pet, Function<OwnedPet, String> petName
    ) {
        return new MessageSpec(key, Map.of("pet", petName.apply(pet)));
    }
}
