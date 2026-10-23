package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.platform.paper.PetEffectApplier;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.bukkit.entity.LivingEntity;

/** Applies active skill-tree levels from the online cache to a live Paper pet entity. */
public final class SkillTreePetEffectApplier implements PetEffectApplier {
    private final Function<PetId, Optional<PetSkillTreeState>> stateLookup;
    private final AttributeEffectAdapter vanillaEffects;
    private final AbilityRegistry abilities;
    private final Consumer<String> unknownAbilityWarning;
    private final Set<String> reportedUnknownAbilities = ConcurrentHashMap.newKeySet();

    /** Creates an applier with cache lookup, vanilla adapter, and custom-ability registry seams. */
    public SkillTreePetEffectApplier(
            Function<PetId, Optional<PetSkillTreeState>> stateLookup,
            AttributeEffectAdapter vanillaEffects,
            AbilityRegistry abilities,
            Consumer<String> unknownAbilityWarning
    ) {
        this.stateLookup = Objects.requireNonNull(stateLookup, "stateLookup");
        this.vanillaEffects = Objects.requireNonNull(vanillaEffects, "vanillaEffects");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.unknownAbilityWarning = Objects.requireNonNull(unknownAbilityWarning, "unknownAbilityWarning");
    }

    @Override
    public void apply(PetId petId, LivingEntity entity) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entity, "entity");
        stateLookup.apply(petId).ifPresentOrElse(
                state -> applyState(petId, entity, state),
                () -> abilities.deactivate(petId));
    }

    @Override
    public void remove(PetId petId) {
        abilities.deactivate(Objects.requireNonNull(petId, "petId"));
    }

    private void applyState(PetId petId, LivingEntity entity, PetSkillTreeState state) {
        if (!state.progress().petId().equals(petId)) {
            throw new IllegalStateException("skill-tree state belongs to a different pet");
        }
        for (AttributeDefinition definition : state.definition().attributes()) {
            var levelProgress = state.progress().attributes().get(definition.id());
            if (levelProgress == null || levelProgress.maxLevel() != definition.maxLevel()) {
                throw new IllegalStateException(
                        "skill-tree progress does not match attribute " + definition.id());
            }
            int activeLevel = levelProgress.activeLevel();
            if (definition.category() == AttributeCategory.VANILLA) {
                vanillaEffects.apply(definition, activeLevel, entity);
            } else {
                updateCustomAbility(petId, entity, definition, activeLevel);
            }
        }
    }

    private void updateCustomAbility(
            PetId petId,
            LivingEntity entity,
            AttributeDefinition definition,
            int activeLevel
    ) {
        var reference = (CustomEffectReference) definition.effect();
        Map<String, String> parameters = parameters(reference, activeLevel);
        if (!abilities.update(reference.abilityId(), petId, entity, activeLevel, parameters)
                && reportedUnknownAbilities.add(reference.abilityId())) {
            unknownAbilityWarning.accept(reference.abilityId());
        }
    }

    private static Map<String, String> parameters(CustomEffectReference reference, int activeLevel) {
        if (activeLevel == 0 || reference.parametersPerLevel().isEmpty()) {
            return Map.of();
        }
        return reference.parametersPerLevel().orElseThrow().get(activeLevel - 1);
    }
}
