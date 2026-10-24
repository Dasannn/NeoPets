package dev.dasannn.neopets.platform.paper.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.CustomEffectReference;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import dev.dasannn.neopets.platform.paper.ability.Ability;
import dev.dasannn.neopets.platform.paper.ability.AbilityContext;
import dev.dasannn.neopets.platform.paper.ability.CooldownTracker;
import dev.dasannn.neopets.platform.paper.ability.DefaultAbilityRegistry;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class SkillTreePetEffectApplierTest {
    @Test
    void definitionReloadAndResetRemoveObsoleteActivations() {
        var petId = new PetId(UUID.randomUUID());
        UUID entityId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        var entity = (LivingEntity) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {LivingEntity.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> entityId;
                    default -> throw new AssertionError(method);
                });
        var owner = (Player) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> ownerId;
                    default -> throw new AssertionError(method);
                });
        var calls = new ArrayList<String>();
        var registry = new DefaultAbilityRegistry(
                List.of(recordingAbility("old", calls), recordingAbility("new", calls)),
                new CooldownTracker(), Clock.systemUTC(),
                ignored -> Optional.of(owner));
        var state = new AtomicReference<PetSkillTreeState>();
        var applier = new SkillTreePetEffectApplier(ignored -> Optional.ofNullable(state.get()),
                new AttributeEffectAdapter(new VanillaEffectCatalog()), registry, ignored -> { });
        state.set(state(petId, "old", 1));
        applier.apply(petId, entity);
        registry.tick(petId, entity, owner);
        state.set(state(petId, "new", 1));
        applier.apply(petId, entity);
        registry.tick(petId, entity, owner);
        // Reapplying an unchanged tree must not restart the ability.
        applier.apply(petId, entity);
        state.set(state(petId, "new", 0));
        applier.apply(petId, entity);
        registry.tick(petId, entity, owner);
        state.set(state(petId, "old", 1));
        applier.apply(petId, entity);
        var replacement = new SkillTreeDefinition(new PetTypeId("cat"), List.of(customAbility()));
        state.set(new PetSkillTreeState(replacement, TreeProgress.empty(petId, replacement)));
        applier.apply(petId, entity);
        registry.tick(petId, entity, owner);
        state.set(state(petId, "new", 1));
        applier.apply(petId, entity);
        state.set(null);
        applier.apply(petId, entity);
        registry.tick(petId, entity, owner);
        assertEquals(List.of("old+", "old tick", "old-", "new+", "new tick", "new-",
                "old+", "old-", "new+", "new-"), calls);
    }

    private static PetSkillTreeState state(PetId petId, String id, int level) {
        var definition = new SkillTreeDefinition(new PetTypeId("cat"), List.of(new AttributeDefinition(
                "ability", AttributeCategory.CUSTOM, 1, new CustomEffectReference(id), List.of(BigDecimal.ZERO))));
        return new PetSkillTreeState(definition, TreeProgress.empty(petId, definition).withLevels("ability", 1, level));
    }

    private static Ability recordingAbility(
            String id, List<String> calls) {
        return new Ability() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public void onActivate(LivingEntity pet, Player owner, int level) {
                calls.add(id + "+");
            }

            @Override
            public void onDeactivate(LivingEntity pet, Player owner, int level) {
                calls.add(id + "-");
            }

            @Override
            public void tick(AbilityContext context) {
                calls.add(id + " tick");
            }
        };
    }

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

    @Test
    void removingALivePetDeactivatesItsCustomAbilities() {
        PetId petId = new PetId(UUID.randomUUID());
        var deactivated = new ArrayList<PetId>();
        AbilityRegistry registry = new AbilityRegistry() {
            @Override
            public boolean update(
                    String abilityId,
                    PetId updatedPetId,
                    LivingEntity entity,
                    int activeLevel,
                    Map<String, String> parameters
            ) {
                return true;
            }

            @Override
            public void deactivate(PetId deactivatedPetId) {
                deactivated.add(deactivatedPetId);
            }
        };
        var applier = new SkillTreePetEffectApplier(
                ignored -> Optional.empty(),
                new AttributeEffectAdapter(new VanillaEffectCatalog()),
                registry,
                ignored -> { });

        applier.remove(petId);

        assertEquals(List.of(petId), deactivated);
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
