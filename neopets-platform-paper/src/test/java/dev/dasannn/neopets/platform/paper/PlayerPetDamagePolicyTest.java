package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerPetDamagePolicyTest {
    @Test
    void defaultRuleOnlyDeniesOwnerDamage() {
        var owner = UUID.randomUUID();
        var otherPlayer = UUID.randomUUID();
        var policy = PlayerPetDamagePolicy.ownerCannotDamage();

        assertTrue(policy.denies(owner, owner));
        assertFalse(policy.denies(owner, otherPlayer));
    }
}
