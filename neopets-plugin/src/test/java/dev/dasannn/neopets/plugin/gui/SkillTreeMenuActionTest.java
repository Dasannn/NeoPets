package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillTreeMenuActionTest {
    @Test
    void dispatchesEveryControlAndNodeAction() {
        assertInstanceOf(SkillTreeMenuAction.PreviousPage.class,
                SkillTreeMenuAction.parse(SkillTreeMenuLayoutFactory.ACTION_PREVIOUS_PAGE).orElseThrow());
        assertInstanceOf(SkillTreeMenuAction.NextPage.class,
                SkillTreeMenuAction.parse(SkillTreeMenuLayoutFactory.ACTION_NEXT_PAGE).orElseThrow());
        assertInstanceOf(SkillTreeMenuAction.Back.class,
                SkillTreeMenuAction.parse(SkillTreeMenuLayoutFactory.ACTION_BACK).orElseThrow());
        assertInstanceOf(SkillTreeMenuAction.Help.class,
                SkillTreeMenuAction.parse(SkillTreeMenuLayoutFactory.ACTION_HELP).orElseThrow());
        assertInstanceOf(SkillTreeMenuAction.Reset.class,
                SkillTreeMenuAction.parse(SkillTreeMenuLayoutFactory.ACTION_RESET).orElseThrow());

        var unlock = assertInstanceOf(SkillTreeMenuAction.Unlock.class,
                SkillTreeMenuAction.parse("skills:unlock:health").orElseThrow());
        var activate = assertInstanceOf(SkillTreeMenuAction.Activate.class,
                SkillTreeMenuAction.parse("skills:activate:regeneration").orElseThrow());
        assertEquals("health", unlock.attributeId());
        assertEquals("regeneration", activate.attributeId());
        assertTrue(SkillTreeMenuAction.parse("pets:close").isEmpty());
        assertTrue(SkillTreeMenuAction.parse("skills:unlock:").isEmpty());
    }
}
