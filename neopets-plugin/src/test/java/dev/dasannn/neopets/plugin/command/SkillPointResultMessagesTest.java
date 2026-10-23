package dev.dasannn.neopets.plugin.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.skilltree.SkillPointResult;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillPointResultMessagesTest {
    @Test
    void mapsEveryOutcomeWithItsPlaceholders() {
        assertEquals(new MessageSpec("skills.points-changed", Map.of("player", "Alex", "points", "7")),
                SkillPointResultMessages.change(new SkillPointResult.Changed(7), "Alex"));
        assertEquals(new MessageSpec("skills.points-insufficient", Map.of(
                        "player", "Alex", "points", "2", "amount", "5")),
                SkillPointResultMessages.change(new SkillPointResult.InsufficientSkillPoints(2, 5), "Alex"));
        assertEquals(new MessageSpec("skills.points-overflow", Map.of("player", "Alex")),
                SkillPointResultMessages.change(new SkillPointResult.Overflow(Integer.MAX_VALUE, 1), "Alex"));
    }
}
