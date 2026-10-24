package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                (player, token, reason) -> cleanups.add(player + ":" + reason));

        long rejected = sessions.started(PLAYER);
        sessions.loaded(PLAYER, rejected);
        sessions.rejected(PLAYER, rejected);
        long newer = sessions.started(PLAYER);
        sessions.loaded(PLAYER, newer);
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
                (player, token, reason) -> cleanups.add(reason));

        long token = sessions.started(PLAYER);
        sessions.loaded(PLAYER, token);
        sessions.joined(PLAYER);
        expiries.getFirst().run();

        assertEquals(List.of(), cleanups);
    }

    @Test
    void rejectedSecondConnectionCannotEvictJoinedFirstConnection() {
        var expiries = new ArrayList<Runnable>();
        var cleanups = new ArrayList<String>();
        var sessions = new PendingLoginSessions(expiries::add,
                (player, token, reason) -> cleanups.add(reason));

        long connected = sessions.started(PLAYER);
        sessions.loaded(PLAYER, connected);
        sessions.joined(PLAYER);
        long duplicate = sessions.started(PLAYER);
        sessions.loaded(PLAYER, duplicate);

        sessions.rejected(PLAYER, duplicate);
        expiries.getLast().run();

        assertEquals(List.of("rejected login"), cleanups);
        assertEquals(connected, sessions.departed(PLAYER));
    }

    @Test
    void cacheSharedByOverlappingLoginsIsCleanedOnlyAfterLastSessionIsRejected() {
        var expiries = new ArrayList<Runnable>();
        var cleanups = new ArrayList<String>();
        var sessions = new PendingLoginSessions(expiries::add,
                (player, token, reason) -> cleanups.add(reason));

        long first = sessions.started(PLAYER);
        long second = sessions.started(PLAYER);
        sessions.loaded(PLAYER, first);
        sessions.loaded(PLAYER, second);

        sessions.rejected(PLAYER, first);
        assertEquals(List.of("rejected login"), cleanups);

        sessions.rejected(PLAYER, second);
        assertEquals(List.of("rejected login", "rejected login"), cleanups);
    }

    @Test
    void abandonmentExpiryStartsOnlyAfterProfileLoadingCompletes() {
        var expiries = new ArrayList<Runnable>();
        var cleanups = new ArrayList<String>();
        var sessions = new PendingLoginSessions(expiries::add,
                (player, token, reason) -> cleanups.add(reason));

        long token = sessions.started(PLAYER);

        assertTrue(expiries.isEmpty());
        assertTrue(cleanups.isEmpty());

        sessions.loaded(PLAYER, token);
        assertEquals(1, expiries.size());

        expiries.getFirst().run();
        assertEquals(List.of("abandoned login"), cleanups);
    }
}
