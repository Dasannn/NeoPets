package dev.dasannn.neopets.storage.cache;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import dev.dasannn.neopets.core.port.PetRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A write-through {@link PetRepository} backed by the online-player cache. */
public final class CachingPetRepository implements PetRepository {
    private final PetRepository delegate;
    private final PlayerDataCache cache;

    /** Creates a caching decorator around the durable repository. */
    public CachingPetRepository(PetRepository delegate, PlayerDataCache cache) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public CompletableFuture<Optional<OwnedPet>> findById(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        var cached = cache.findPet(petId);
        return cached.hit() ? CompletableFuture.completedFuture(cached.value()) : delegate.findById(petId);
    }

    @Override
    public CompletableFuture<List<OwnedPet>> findByOwner(PlayerId ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        var cached = cache.findPetsByOwner(ownerId);
        return cached.hit()
                ? CompletableFuture.completedFuture(cached.value().orElseThrow())
                : delegate.findByOwner(ownerId);
    }

    @Override
    public CompletableFuture<Void> save(OwnedPet pet) {
        Objects.requireNonNull(pet, "pet");
        return cache.savePet(pet, () -> delegate.save(pet));
    }

    @Override
    public CompletableFuture<Void> deleteById(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        return cache.deletePet(petId, () -> delegate.deleteById(petId));
    }
}
