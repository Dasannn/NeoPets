package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetId;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.junit.jupiter.api.Test;

class FelineLandingListenerTest {
    @Test
    void registryActivationNodeDeactivationAndDismissalControlFallEvents() {
        var fixture = new Fixture();
        fixture.assertDamage(fixture.owner, DamageCause.FALL, false);
        fixture.activate();
        fixture.assertDamage(fixture.owner, DamageCause.FALL, true);
        fixture.assertDamage(fixture.owner, DamageCause.FIRE, false);
        fixture.assertDamage(proxy(Player.class), DamageCause.FALL, false);
        fixture.assertDamage(fixture.cat, DamageCause.FALL, false);

        fixture.registry.update(FelineLandingAbility.ID, fixture.petId, fixture.cat, 0, Map.of());
        fixture.assertDamage(fixture.owner, DamageCause.FALL, false);
        fixture.activate();
        fixture.assertDamage(fixture.owner, DamageCause.FALL, true);
        fixture.registry.deactivate(fixture.petId);
        fixture.assertDamage(fixture.owner, DamageCause.FALL, false);
    }

    @Test
    void deathEventRemovesProtectionBeforeRegistryCleanup() {
        var fixture = new Fixture();
        fixture.activate();
        fixture.assertDamage(fixture.owner, DamageCause.FALL, true);

        fixture.listener.onEntityDeath(new EntityDeathEvent(fixture.cat, fixture.source, new ArrayList<>()));

        fixture.assertDamage(fixture.owner, DamageCause.FALL, false);
        fixture.registry.deactivate(fixture.petId);
        fixture.assertDamage(fixture.owner, DamageCause.FALL, false);
    }

    private static final class Fixture {
        private final Player owner = proxy(Player.class);
        private final LivingEntity cat = proxy(LivingEntity.class);
        private final PetId petId = new PetId(UUID.randomUUID());
        private final DamageSource source = proxy(DamageSource.class);
        private final FelineLandingPolicy policy = new FelineLandingPolicy();
        private final FelineLandingListener listener = new FelineLandingListener(policy);
        private final DefaultAbilityRegistry registry = new DefaultAbilityRegistry(
                List.of(new FelineLandingAbility(policy)), new CooldownTracker(), Clock.systemUTC(),
                ignored -> Optional.of(owner));

        private void activate() {
            registry.update(FelineLandingAbility.ID, petId, cat, 1, Map.of());
        }

        // Paper currently marks all EntityDamageEvent constructors for removal.
        @SuppressWarnings("removal")
        private void assertDamage(Entity entity, DamageCause cause, boolean cancelled) {
            var event = new EntityDamageEvent(entity, cause, source, 5);
            listener.onDamage(event);
            assertEquals(cancelled, event.isCancelled());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        UUID id = UUID.randomUUID();
        return (T) Proxy.newProxyInstance(FelineLandingListenerTest.class.getClassLoader(),
                new Class<?>[] {type}, (object, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> id;
                    default -> throw new AssertionError(method);
                });
    }
}
