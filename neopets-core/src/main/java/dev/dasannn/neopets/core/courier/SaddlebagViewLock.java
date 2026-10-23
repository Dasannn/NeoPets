package dev.dasannn.neopets.core.courier;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-pet exclusive inventory-view locks whose leases release only after a durable save succeeds. */
public final class SaddlebagViewLock {
    private final Map<PetId, Lease> leases = new HashMap<>();

    /** Acquires a pet lock, or returns empty while another view/save owns it. */
    public synchronized Optional<Lease> tryAcquire(PetId petId, PlayerId viewer) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(viewer, "viewer");
        if (leases.containsKey(petId)) return Optional.empty();
        Lease lease = new Lease(petId, viewer);
        leases.put(petId, lease);
        return Optional.of(lease);
    }

    /** Returns whether a view or its pending save currently owns this pet lock. */
    public synchronized boolean isLocked(PetId petId) {
        return leases.containsKey(Objects.requireNonNull(petId, "petId"));
    }

    /** One exclusive view lease. */
    public final class Lease {
        private final PetId petId;
        private final PlayerId viewer;
        private final AtomicBoolean closing = new AtomicBoolean();

        private Lease(PetId petId, PlayerId viewer) {
            this.petId = petId;
            this.viewer = viewer;
        }

        public PetId petId() { return petId; }
        public PlayerId viewer() { return viewer; }

        /** Releases this lease only after the supplied durable save succeeds. */
        public void releaseAfter(CompletionStage<?> save) {
            Objects.requireNonNull(save, "save");
            if (!closing.compareAndSet(false, true)) return;
            save.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    synchronized (SaddlebagViewLock.this) { leases.remove(petId, this); }
                }
            });
        }

        /** Abandons a lease before any mutable inventory has been exposed. */
        public void abort() {
            if (!closing.compareAndSet(false, true)) return;
            synchronized (SaddlebagViewLock.this) { leases.remove(petId, this); }
        }
    }
}
