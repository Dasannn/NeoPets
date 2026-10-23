package dev.dasannn.neopets.plugin.command;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.skilltree.ActivateResult;
import dev.dasannn.neopets.core.skilltree.ResetResult;
import dev.dasannn.neopets.core.skilltree.UnlockResult;
import dev.dasannn.neopets.plugin.gui.PaidResetResult;
import java.util.Map;
import java.util.function.Function;

/** Exhaustive localized-message mapping for skill-tree menu operations. */
public final class SkillTreeResultMessages {
    private SkillTreeResultMessages() {
    }

    public static MessageSpec unlock(
            UnlockResult result,
            Function<String, String> attributeName,
            Function<OwnedPet, String> petName
    ) {
        return switch (result) {
            case UnlockResult.Unlocked unlocked -> new MessageSpec("skills.node-unlocked", Map.of(
                    "attribute", attributeName.apply(unlocked.attributeId()),
                    "level", Integer.toString(unlocked.level())));
            case UnlockResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case UnlockResult.PetDead dead -> pet("pets.dead-unavailable", dead.pet(), petName);
            case UnlockResult.PetInTransit transit -> pet("pets.in-transit", transit.pet(), petName);
            case UnlockResult.UnknownAttribute ignored -> MessageSpec.of("errors.internal");
            case UnlockResult.MaxLevel ignored -> MessageSpec.of("skills.already-unlocked");
            case UnlockResult.NodeLimitReached limit -> new MessageSpec(
                    "skills.node-limit", Map.of("limit", Integer.toString(limit.maxNodesPerPet())));
            case UnlockResult.InsufficientFunds funds -> new MessageSpec(
                    "skills.not-enough-money", Map.of("cost", money(funds.required())));
            case UnlockResult.Failed ignored -> MessageSpec.of("errors.internal");
        };
    }

    public static MessageSpec activate(
            ActivateResult result,
            Function<String, String> attributeName,
            Function<OwnedPet, String> petName
    ) {
        return switch (result) {
            case ActivateResult.Activated activated -> new MessageSpec("skills.node-activated", Map.of(
                    "attribute", attributeName.apply(activated.attributeId()),
                    "level", Integer.toString(activated.level())));
            case ActivateResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
            case ActivateResult.PetDead dead -> pet("pets.dead-unavailable", dead.pet(), petName);
            case ActivateResult.PetInTransit transit -> pet("pets.in-transit", transit.pet(), petName);
            case ActivateResult.UnknownAttribute ignored -> MessageSpec.of("errors.internal");
            case ActivateResult.NoUnlockedLevel ignored -> MessageSpec.of("skills.already-active");
            case ActivateResult.SkillPointLimitReached limit -> new MessageSpec(
                    "skills.point-limit", Map.of("limit", Integer.toString(limit.maxSkillPointsPerPet())));
            case ActivateResult.InsufficientSkillPoints ignored -> MessageSpec.of("skills.not-enough-points");
            case ActivateResult.Failed ignored -> MessageSpec.of("errors.internal");
        };
    }

    public static MessageSpec reset(PaidResetResult result, String petName) {
        return switch (result) {
            case PaidResetResult.InsufficientFunds funds -> new MessageSpec(
                    "skills.not-enough-money", Map.of("cost", money(funds.required())));
            case PaidResetResult.Failed ignored -> MessageSpec.of("errors.internal");
            case PaidResetResult.Completed completed -> switch (completed.result()) {
                case ResetResult.Reset reset -> new MessageSpec("skills.reset", Map.of(
                        "pet", petName, "points", Integer.toString(reset.refundedSkillPoints())));
                case ResetResult.NotOwned ignored -> MessageSpec.of("pets.not-owned");
                case ResetResult.Failed ignored -> MessageSpec.of("errors.internal");
            };
        };
    }

    private static String money(java.math.BigDecimal value) {
        return "$" + value.stripTrailingZeros().toPlainString();
    }

    private static MessageSpec pet(
            String key, OwnedPet pet, Function<OwnedPet, String> petName
    ) {
        return new MessageSpec(key, Map.of("pet", petName.apply(pet)));
    }
}
