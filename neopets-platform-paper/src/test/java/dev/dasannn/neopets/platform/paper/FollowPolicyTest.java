package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FollowPolicyTest {
    private final FollowPolicy policy = new FollowPolicy(4.0, 16.0);

    @Test
    void staysIdleAtAndInsideFollowDistance() {
        assertEquals(FollowAction.IDLE, policy.choose(true, 0.0, true));
        assertEquals(FollowAction.IDLE, policy.choose(true, 16.0, true));
    }

    @Test
    void pathfindsOnlyBetweenDistanceThresholds() {
        assertEquals(FollowAction.PATHFIND, policy.choose(true, 16.01, true));
        assertEquals(FollowAction.PATHFIND, policy.choose(true, 256.0, true));
    }

    @Test
    void teleportsBeyondTeleportDistanceOrAcrossWorlds() {
        assertEquals(FollowAction.TELEPORT, policy.choose(true, 256.01, true));
        assertEquals(FollowAction.TELEPORT, policy.choose(false, 0.0, true));
    }

    @Test
    void neverTeleportsWhileTheOwnerIsAirborne() {
        assertEquals(FollowAction.PATHFIND, policy.choose(true, 256.01, false));
        assertEquals(FollowAction.IDLE, policy.choose(false, 0.0, false));
        assertEquals(FollowAction.PATHFIND, policy.choose(true, 100.0, false));
        assertEquals(FollowAction.IDLE, policy.choose(true, 4.0, false));
    }

    @Test
    void rejectsInvalidConfigurationAndDistance() {
        assertThrows(IllegalArgumentException.class, () -> new FollowPolicy(-1.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new FollowPolicy(10.0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> new FollowPolicy(10.0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> policy.choose(true, -1.0, true));
        assertThrows(IllegalArgumentException.class, () -> policy.choose(true, Double.POSITIVE_INFINITY, true));
    }
}
