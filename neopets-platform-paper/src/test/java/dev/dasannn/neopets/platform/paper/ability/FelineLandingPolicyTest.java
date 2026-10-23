package dev.dasannn.neopets.platform.paper.ability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class FelineLandingPolicyTest {
    @Test
    void reloadingToggleChangesProtectionWithoutResummoning() {
        var enabled = new AtomicBoolean(true);
        var policy = new FelineLandingPolicy(enabled::get);
        UUID owner = UUID.randomUUID();
        policy.activate(owner, UUID.randomUUID());
        assertTrue(policy.preventsFallDamage(owner));
        enabled.set(false);
        assertFalse(policy.preventsFallDamage(owner));
        enabled.set(true);
        assertTrue(policy.preventsFallDamage(owner));
    }

    @Test
    void inactiveOwnerIsNotProtected() {
        assertFalse(new FelineLandingPolicy().preventsFallDamage(UUID.randomUUID()));
    }

    @Test
    void activeCatProtectsUntilItIsDismissed() {
        var policy = new FelineLandingPolicy();
        UUID owner = UUID.randomUUID();
        UUID cat = UUID.randomUUID();

        policy.activate(owner, cat);
        assertTrue(policy.preventsFallDamage(owner));

        policy.deactivate(owner, cat);
        assertFalse(policy.preventsFallDamage(owner));
    }

    @Test
    void deadCatStopsProtectingImmediately() {
        var policy = new FelineLandingPolicy();
        UUID owner = UUID.randomUUID();
        UUID cat = UUID.randomUUID();
        policy.activate(owner, cat);

        policy.deactivatePet(cat);

        assertFalse(policy.preventsFallDamage(owner));
    }

    @Test
    void oneDeadCatDoesNotRemoveProtectionFromAnotherLiveSummonedCat() {
        var policy = new FelineLandingPolicy();
        UUID owner = UUID.randomUUID();
        UUID deadCat = UUID.randomUUID();
        UUID liveCat = UUID.randomUUID();
        policy.activate(owner, deadCat);
        policy.activate(owner, liveCat);

        policy.deactivate(owner, deadCat);

        assertTrue(policy.preventsFallDamage(owner));
        policy.deactivate(owner, liveCat);
        assertFalse(policy.preventsFallDamage(owner));
    }
}
