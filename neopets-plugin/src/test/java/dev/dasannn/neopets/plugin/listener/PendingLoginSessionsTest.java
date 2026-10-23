package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingLoginSessionsTest {
    private static final PlayerId PLAYER = new PlayerId(
            UUID.fromString("00000000-0000-0000-0000-000000000001"));

    @Test
    void rejectedSuccessfulLoginIsCleanedAndExpiryDoesNotCleanANewerSession() {
        var expiries = new ArrayList<Runnable>();
        var cleanups = new ArrayList<String>();
        var sessions = new PendingLoginSessions(expiries::add,
                (player, reason) -> cleanups.add(player + ":" + reason));

        long rejected = sessions.loaded(PLAYER);
        sessions.rejected(PLAYER, rejected);
        sessions.loaded(PLAYER);
        expiries.getFirst().run();

        assertEquals(List.of(PLAYER + ":rejected login"), cleanups);

        expiries.getLast().run();
        assertEquals(List.of(PLAYER + ":rejected login", PLAYER + ":abandoned login"), cleanups);
    }

    @Test
    void joinedSessionIsNotEvictedByItsExpiry() {
        var expiries = new ArrayList<Runnable>();
        var cleanups = new ArrayList<String>();
        var sessions = new PendingLoginSessions(expiries::add,
                (player, reason) -> cleanups.add(reason));

        sessions.loaded(PLAYER);
        sessions.joined(PLAYER);
        expiries.getFirst().run();

        assertEquals(List.of(), cleanups);
    }
}
