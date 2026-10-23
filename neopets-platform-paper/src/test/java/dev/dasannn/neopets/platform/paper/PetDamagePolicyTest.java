package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PetDamagePolicyTest {
    @Test
    void pvpToggleOnlyAllowsDamageFromOtherPlayersWhenEnabled() {
        var pvp = new AtomicBoolean(false);
        var policy = new PetDamagePolicy(() -> true, pvp::get);
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();

        assertTrue(policy.denies(owner, owner));
        assertTrue(policy.denies(owner, other));
        pvp.set(true);
        assertTrue(policy.denies(owner, owner));
        assertFalse(policy.denies(owner, other));
    }

    @Test
    void deathToggleOnlyStopsDamageThatWouldBeLethal() {
        var death = new AtomicBoolean(false);
        var policy = new PetDamagePolicy(death::get, () -> true);

        assertFalse(policy.preventsDeath(10.0, 9.99));
        assertTrue(policy.preventsDeath(10.0, 10.0));
        assertTrue(policy.preventsDeath(10.0, 12.0));
        death.set(true);
        assertFalse(policy.preventsDeath(10.0, 12.0));
    }
}
