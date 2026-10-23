package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Tracks the gap between asynchronous profile loading and a completed player join. */
final class PendingLoginSessions {
    private final Map<PlayerId, Long> sessions = new HashMap<>();
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
            sessions.put(playerId, token);
        }
        try {
            scheduleExpiry.accept(() -> abandon(playerId, token, "abandoned login"));
        } catch (RuntimeException failure) {
            synchronized (sessions) {
                sessions.remove(playerId, token);
            }
            throw failure;
        }
        return token;
    }

    void joined(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        synchronized (sessions) {
            sessions.remove(playerId);
        }
    }

    void rejected(PlayerId playerId, long token) {
        abandon(playerId, token, "rejected login");
    }

    void rejected(PlayerId playerId) {
        Long token;
        synchronized (sessions) {
            token = sessions.get(playerId);
        }
        if (token != null) {
            abandon(playerId, token, "rejected login");
        }
    }

    private void abandon(PlayerId playerId, long token, String reason) {
        synchronized (sessions) {
            if (!sessions.remove(playerId, token)) {
                return;
            }
        }
        cleanup.accept(playerId, reason);
    }
}
