package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Tracks which pending or joined connections currently own each player's shared cache entry. */
final class PendingLoginSessions {
    private final Map<PlayerId, LinkedHashMap<Long, SessionState>> sessions = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Consumer<Runnable> scheduleExpiry;
    private final Cleanup cleanup;

    PendingLoginSessions(Consumer<Runnable> scheduleExpiry, Cleanup cleanup) {
        this.scheduleExpiry = Objects.requireNonNull(scheduleExpiry, "scheduleExpiry");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    long started(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long token = sequence.incrementAndGet();
        synchronized (sessions) {
            sessions.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                    .put(token, SessionState.LOADING);
        }
        return token;
    }

    void loaded(PlayerId playerId, long token) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null || !playerSessions.replace(
                    token, SessionState.LOADING, SessionState.PENDING)) {
                return;
            }
        }
        try {
            scheduleExpiry.accept(() -> abandon(playerId, token, "abandoned login"));
        } catch (RuntimeException failure) {
            abandon(playerId, token, "abandoned login");
            throw failure;
        }
    }

    Long joined(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null) {
                return null;
            }
            Map.Entry<Long, SessionState> pending = playerSessions.entrySet().stream()
                    .filter(entry -> entry.getValue() == SessionState.PENDING)
                    .findFirst()
                    .orElse(null);
            if (pending == null) {
                return null;
            }
            pending.setValue(SessionState.JOINED);
            return pending.getKey();
        }
    }

    /** Removes one connected session and returns the cache lease that it owned. */
    Long departed(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null) {
                return null;
            }
            Long joinedToken = playerSessions.entrySet().stream()
                    .filter(entry -> entry.getValue() == SessionState.JOINED)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (joinedToken != null) {
                remove(playerId, joinedToken);
            }
            return joinedToken;
        }
    }

    void rejected(PlayerId playerId, long token) {
        abandon(playerId, token, "rejected login");
    }

    void rejected(PlayerId playerId) {
        Long token;
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            token = playerSessions == null ? null : playerSessions.entrySet().stream()
                    .filter(entry -> entry.getValue() == SessionState.PENDING)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
        if (token != null) {
            abandon(playerId, token, "rejected login");
        }
    }

    private void abandon(PlayerId playerId, long token, String reason) {
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null || playerSessions.get(token) == SessionState.JOINED) {
                return;
            }
            if (!playerSessions.containsKey(token)) {
                return;
            }
            remove(playerId, token);
        }
        cleanup.accept(playerId, token, reason);
    }

    private void remove(PlayerId playerId, long token) {
        LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
        if (playerSessions == null) {
            return;
        }
        playerSessions.remove(token);
        if (playerSessions.isEmpty()) {
            sessions.remove(playerId);
        }
    }

    private enum SessionState {
        LOADING,
        PENDING,
        JOINED
    }

    @FunctionalInterface
    interface Cleanup {
        void accept(PlayerId playerId, long token, String reason);
    }
}
