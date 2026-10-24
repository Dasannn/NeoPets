package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.util.Objects;

/** Immutable configured definition and current progress used to update one live pet. */
public record PetSkillTreeState(SkillTreeDefinition definition, TreeProgress progress) {
    public PetSkillTreeState {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(progress, "progress");
    }
}
