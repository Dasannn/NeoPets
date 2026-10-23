package dev.dasannn.neopets.storage.cache;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.port.TreeProgressRepository;
import dev.dasannn.neopets.core.skilltree.TreeProgress;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A write-through {@link TreeProgressRepository} backed by the online-player cache. */
public final class CachingTreeProgressRepository implements TreeProgressRepository {
    private final TreeProgressRepository delegate;
    private final PlayerDataCache cache;

    /** Creates a caching decorator around the durable repository. */
    public CachingTreeProgressRepository(TreeProgressRepository delegate, PlayerDataCache cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public CompletableFuture<Optional<TreeProgress>> load(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        var cached = cache.findTreeProgress(petId);
        return cached.hit() ? CompletableFuture.completedFuture(cached.value()) : delegate.load(petId);
    }

    @Override
    public CompletableFuture<Void> save(TreeProgress progress) {
        Objects.requireNonNull(progress, "progress");
        return cache.saveTreeProgress(progress, () -> delegate.save(progress));
    }
}
