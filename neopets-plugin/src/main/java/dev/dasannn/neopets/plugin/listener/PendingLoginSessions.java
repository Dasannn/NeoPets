package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Tracks which pending or joined connections currently own each player's shared cache entry. */
final class PendingLoginSessions {
    private final Map<PlayerId, LinkedHashMap<Long, SessionState>> sessions = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final Consumer<Runnable> scheduleExpiry;
    private final BiConsumer<PlayerId, String> cleanup;

    PendingLoginSessions(Consumer<Runnable> scheduleExpiry, BiConsumer<PlayerId, String> cleanup) {
        this.scheduleExpiry = Objects.requireNonNull(scheduleExpiry, "scheduleExpiry");
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
    }

    long loaded(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long token = sequence.incrementAndGet();
        synchronized (sessions) {
            sessions.computeIfAbsent(playerId, ignored -> new LinkedHashMap<>())
                    .put(token, SessionState.PENDING);
        }
        try {
            scheduleExpiry.accept(() -> abandon(playerId, token, "abandoned login"));
        } catch (RuntimeException failure) {
            synchronized (sessions) {
                remove(playerId, token);
            }
            throw failure;
        }
        return token;
    }

    void joined(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null) {
                return;
            }
            playerSessions.entrySet().stream()
                    .filter(entry -> entry.getValue() == SessionState.PENDING)
                    .findFirst()
                    .ifPresent(entry -> entry.setValue(SessionState.JOINED));
        }
    }

    /** Removes one connected session and reports whether its cache is no longer shared. */
    boolean departed(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null) {
                return true;
            }
            Long joinedToken = playerSessions.entrySet().stream()
                    .filter(entry -> entry.getValue() == SessionState.JOINED)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            if (joinedToken != null) {
                remove(playerId, joinedToken);
            }
            return !sessions.containsKey(playerId);
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
        boolean cleanupRequired;
        synchronized (sessions) {
            LinkedHashMap<Long, SessionState> playerSessions = sessions.get(playerId);
            if (playerSessions == null || playerSessions.get(token) != SessionState.PENDING) {
                return;
            }
            remove(playerId, token);
            cleanupRequired = !sessions.containsKey(playerId);
        }
        if (cleanupRequired) {
            cleanup.accept(playerId, reason);
        }
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
        PENDING,
        JOINED
    }
}
