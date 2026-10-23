package dev.dasannn.neopets.platform.paper.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class SkillTreePetEffectApplierTest {
    @Test
    void delegatesCustomLevelsAndParametersToAbilityRegistry() {
        PetId petId = new PetId(UUID.randomUUID());
        AttributeDefinition ability = customAbility();
        SkillTreeDefinition definition = new SkillTreeDefinition(new PetTypeId("cat"), List.of(ability));
        TreeProgress progress = TreeProgress.empty(petId, definition).withLevels("landing", 2, 2);
        var updates = new ArrayList<String>();
        AbilityRegistry registry = (id, actualPetId, entity, level, parameters) -> {
            updates.add(id + ":" + level + ":" + parameters.get("strength"));
            return true;
        };
        var applier = applier(petId, definition, progress, registry, ignored -> { });

        applier.apply(petId, unusedEntity());

        assertEquals(List.of("feline_landing:2:strong"), updates);
    }

    @Test
    void reportsEachUnknownCustomAbilityOnlyOnce() {
        PetId petId = new PetId(UUID.randomUUID());
        AttributeDefinition ability = customAbility();
        SkillTreeDefinition definition = new SkillTreeDefinition(new PetTypeId("cat"), List.of(ability));
        TreeProgress progress = TreeProgress.empty(petId, definition).withLevels("landing", 1, 1);
        var warnings = new ArrayList<String>();
        var applier = applier(petId, definition, progress, AbilityRegistry.empty(), warnings::add);

        applier.apply(petId, unusedEntity());
        applier.apply(petId, unusedEntity());

        assertEquals(List.of("feline_landing"), warnings);
    }

    private static SkillTreePetEffectApplier applier(
            PetId petId,
            SkillTreeDefinition definition,
            TreeProgress progress,
            AbilityRegistry registry,
            java.util.function.Consumer<String> warning
    ) {
        return new SkillTreePetEffectApplier(
                requested -> requested.equals(petId)
                        ? Optional.of(new PetSkillTreeState(definition, progress)) : Optional.empty(),
                new AttributeEffectAdapter(new VanillaEffectCatalog()),
                registry,
                warning);
    }

    private static AttributeDefinition customAbility() {
        return new AttributeDefinition(
                "landing",
                AttributeCategory.CUSTOM,
                2,
                new CustomEffectReference("feline_landing", List.of(
                        Map.of("strength", "light"), Map.of("strength", "strong"))),
                List.of(BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static LivingEntity unusedEntity() {
        return (LivingEntity) Proxy.newProxyInstance(
                SkillTreePetEffectApplierTest.class.getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "unused living entity";
                    default -> throw new AssertionError("Unexpected entity call: " + method);
                });
    }
}
