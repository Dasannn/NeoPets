package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.skilltree.ActivateResult;
import dev.dasannn.neopets.core.skilltree.UnlockResult;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillTreeResultMessagesTest {
    @Test
    void mapsLimitsFundsAndOwnershipToLocalizedMessages() {
        assertEquals("skills.node-limit",
                SkillTreeResultMessages.unlock(new UnlockResult.NodeLimitReached(10), value -> value,
                        pet -> "pet").key());
        MessageSpec funds = SkillTreeResultMessages.unlock(
                new UnlockResult.InsufficientFunds(BigDecimal.valueOf(250)), value -> value, pet -> "pet");
        assertEquals("skills.not-enough-money", funds.key());
        assertEquals("$250", funds.placeholders().get("cost"));
        assertEquals("skills.point-limit", SkillTreeResultMessages.activate(
                new ActivateResult.SkillPointLimitReached(3), value -> value, pet -> "pet").key());
        assertEquals("pets.not-owned", SkillTreeResultMessages.activate(
                new ActivateResult.NotOwned(new PetId(UUID.randomUUID())), value -> value,
                pet -> "pet").key());
    }
}
