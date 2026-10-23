package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.platform.paper.effect.AttributeEffectAdapter;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class DefaultAbilityRegistryTest {
    @Test
    void radiusBonusReadsActiveScentAndAlertRangeModifiers() {
        var modifiers = new HashMap<NamespacedKey, AttributeModifier>();
        var instance = (AttributeInstance) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {AttributeInstance.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getModifier")) {
                        return modifiers.get(args[0]);
                    }
                    throw new AssertionError(method);
                });
        assertEquals(0, DefaultAbilityRegistry.detectionRadiusBonus(instance));
        for (String branch : List.of("scent", "alert_range")) {
            var key = AttributeEffectAdapter.modifierKey(branch);
            for (double amount : new double[] {4, 8, 12, 16, 20}) {
                modifiers.put(key, new AttributeModifier(key, amount,
                        AttributeModifier.Operation.ADD_NUMBER));
                assertEquals(amount, DefaultAbilityRegistry.detectionRadiusBonus(instance));
            }
            modifiers.remove(key);
            assertEquals(0, DefaultAbilityRegistry.detectionRadiusBonus(instance));
        }
        assertEquals(0, DefaultAbilityRegistry.detectionRadiusBonus((AttributeInstance) null));
    }

    @Test
    void maintenanceEvictsExpiredDismissedPetCooldownsAndPreservesPendingOnes() {
        var cooldowns = new CooldownTracker();
        var expired = new PetId(UUID.randomUUID());
        var pending = new PetId(UUID.randomUUID());
        cooldowns.claim(expired, "lookout", Duration.ofSeconds(5), Instant.EPOCH);
        cooldowns.claim(pending, "lookout", Duration.ofSeconds(10), Instant.EPOCH);
        var registry = new DefaultAbilityRegistry(List.of(), cooldowns,
                Clock.fixed(Instant.EPOCH.plusSeconds(5), ZoneOffset.UTC));
        registry.deactivate(expired);
        registry.deactivate(pending);
        registry.maintain();
        assertEquals(0, cooldowns.evictExpired(Instant.EPOCH.plusSeconds(5)));
        assertFalse(cooldowns.claim(pending, "lookout", Duration.ofSeconds(10), Instant.EPOCH.plusSeconds(5)));
    }

    @Test
    void knownAbilityActivatesTicksAndDeactivatesOnlyAtPositiveLevels() {
        var ability = new RecordingAbility("test");
        var owner = player(UUID.randomUUID());
        var pet = livingEntity(UUID.randomUUID());
        var petId = new PetId(UUID.randomUUID());
        var registry = registry(List.of(ability), owner);

        assertTrue(registry.update("test", petId, pet, 1, Map.of()));
        registry.tick(petId, pet, owner);
        assertTrue(registry.update("test", petId, pet, 0, Map.of()));
        registry.tick(petId, pet, owner);

        assertEquals(1, ability.activations);
        assertEquals(1, ability.ticks);
        assertEquals(1, ability.deactivations);
    }

    @Test
    void unknownIdsAreRejectedAndDuplicateRegistrationsFail() {
        var owner = player(UUID.randomUUID());
        var registry = registry(List.of(new RecordingAbility("known")), owner);

        assertFalse(registry.update(
                "missing", new PetId(UUID.randomUUID()), livingEntity(UUID.randomUUID()), 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> registry(
                List.of(new RecordingAbility("same"), new RecordingAbility("same")), owner));
    }

    @Test
    void shippedAbilitiesAreRegisteredUnderSkillTreeEffectIds() {
        var registry = new DefaultAbilityRegistry(List.of(
                new ZarpazoAbility(() -> new ZarpazoAbility.Settings(Duration.ofSeconds(10), 6, 4)),
                new VigiaAbility(() -> new VigiaAbility.Settings(
                        Duration.ofSeconds(5), 16, Duration.ofSeconds(5))),
                new BuscatesorosAbility(() -> new BuscatesorosAbility.Settings(
                        Duration.ofSeconds(30), 16, Duration.ofSeconds(5), 32, 8192,
                        Set.of(org.bukkit.Material.DIAMOND_ORE)), (owner, count) -> { }),
                new FelineLandingAbility(new FelineLandingPolicy())),
                new CooldownTracker(), Clock.systemUTC());

        assertEquals(Set.of("claw_swipe", "lookout", "treasure_hunter", "feline_landing"),
                registry.registeredIds());
    }

    @Test
    void cooldownSurvivesEntityReplacementAndDismissal() {
        var ability = new CooldownClaimingAbility();
        var owner = player(UUID.randomUUID());
        var petId = new PetId(UUID.randomUUID());
        var registry = registry(List.of(ability), owner);
        var firstEntity = livingEntity(UUID.randomUUID());
        var replacement = livingEntity(UUID.randomUUID());

        registry.update(ability.id(), petId, firstEntity, 1, Map.of());
        registry.tick(petId, firstEntity, owner);
        registry.update(ability.id(), petId, replacement, 1, Map.of());
        registry.tick(petId, replacement, owner);
        registry.deactivate(petId);
        registry.update(ability.id(), petId, firstEntity, 1, Map.of());
        registry.tick(petId, firstEntity, owner);

        assertEquals(1, ability.claimedUses);
    }

    private static DefaultAbilityRegistry registry(List<? extends Ability> abilities, Player owner) {
        return new DefaultAbilityRegistry(
                abilities,
                new CooldownTracker(),
                Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC),
                ignored -> Optional.of(owner));
    }

    private static LivingEntity livingEntity(UUID id) {
        return proxy(LivingEntity.class, id);
    }

    private static Player player(UUID id) {
        return proxy(Player.class, id);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, UUID id) {
        return (T) Proxy.newProxyInstance(
                DefaultAbilityRegistryTest.class.getClassLoader(),
                new Class<?>[] {type},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOnline", "isValid" -> true;
                    case "toString" -> type.getSimpleName() + "[" + id + "]";
                    default -> throw new AssertionError("Unexpected call: " + method);
                });
    }

    private static final class RecordingAbility implements Ability {
        private final String id;
        private int activations;
        private int ticks;
        private int deactivations;

        private RecordingAbility(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void onActivate(LivingEntity pet, Player owner, int level) {
            activations++;
        }

        @Override
        public void onDeactivate(LivingEntity pet, Player owner, int level) {
            deactivations++;
        }

        @Override
        public void tick(AbilityContext context) {
            ticks++;
        }
    }

    private static final class CooldownClaimingAbility implements Ability {
        private int claimedUses;

        @Override
        public String id() {
            return "cooldown_test";
        }

        @Override
        public void onActivate(LivingEntity pet, Player owner, int level) {
        }

        @Override
        public void onDeactivate(LivingEntity pet, Player owner, int level) {
        }

        @Override
        public void tick(AbilityContext context) {
            if (context.claimCooldown(Duration.ofMinutes(1))) {
                claimedUses++;
            }
        }
    }
}
