package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.pet.ReviveResult;
import java.util.Objects;

/** Exhaustive business outcomes exposed by {@code /neopets revive}. */
public sealed interface ReviveCommandResult
        permits ReviveCommandResult.Revived, ReviveCommandResult.PlayerOffline,
                ReviveCommandResult.NoSoulItem, ReviveCommandResult.InvalidSoul,
                ReviveCommandResult.WrongOwner, ReviveCommandResult.PetNotDead {
    /** The pet was revived with the reserved main-hand soul item. */
    record Revived(OwnedPet pet) implements ReviveCommandResult {
        public Revived {
            Objects.requireNonNull(pet, "pet");
        }
    }

    /** The named command target is not currently online. */
    record PlayerOffline(String playerName) implements ReviveCommandResult {
        public PlayerOffline {
            Objects.requireNonNull(playerName, "playerName");
        }
    }

    /** The target's main hand does not carry a soul marker. */
    record NoSoulItem() implements ReviveCommandResult {
    }

    /** The soul identity is malformed, mismatched, absent in storage, or already consumed. */
    record InvalidSoul() implements ReviveCommandResult {
    }

    /** The soul or represented pet belongs to another player. */
    record WrongOwner() implements ReviveCommandResult {
    }

    /** The represented pet is not currently dead. */
    record PetNotDead() implements ReviveCommandResult {
    }

    /** Maps every core revival result to its command-level business outcome. */
    static ReviveCommandResult fromCore(ReviveResult result) {
        Objects.requireNonNull(result, "result");
        return switch (result) {
            case ReviveResult.Revived revived -> new Revived(revived.pet());
            case ReviveResult.InvalidSoul ignored -> new InvalidSoul();
            case ReviveResult.NotOwned ignored -> new WrongOwner();
            case ReviveResult.AlreadyAlive ignored -> new PetNotDead();
            case ReviveResult.InTransit ignored -> new PetNotDead();
        };
    }
}
