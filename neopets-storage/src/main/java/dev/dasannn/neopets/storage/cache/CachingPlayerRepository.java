package dev.dasannn.neopets.storage.cache;

import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.model.PlayerProfile;
import dev.dasannn.neopets.core.port.PlayerRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A write-through {@link PlayerRepository} backed by the online-player cache. */
public final class CachingPlayerRepository implements PlayerRepository {
    private final PlayerRepository delegate;
    private final PlayerDataCache cache;

    /** Creates a caching decorator around the durable repository. */
    public CachingPlayerRepository(PlayerRepository delegate, PlayerDataCache cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> findById(PlayerId playerId) {
        Objects.requireNonNull(playerId, "playerId");
        var cached = cache.findProfile(playerId);
        return cached.hit() ? CompletableFuture.completedFuture(cached.value()) : delegate.findById(playerId);
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return cache.saveProfile(profile, () -> delegate.save(profile));
    }
}
