package dev.dasannn.neopets.core.courier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class SaddlebagViewLockTest {
    private static final PetId PET = new PetId(new UUID(1, 1));
    private static final PlayerId FIRST = new PlayerId(new UUID(0, 1));
    private static final PlayerId SECOND = new PlayerId(new UUID(0, 2));

    @Test
    void deniesSecondViewerUntilCloseSaveHasSettled() {
        var locks = new SaddlebagViewLock();
        var first = locks.tryAcquire(PET, FIRST).orElseThrow();
        assertTrue(locks.tryAcquire(PET, SECOND).isEmpty());

        var save = new CompletableFuture<Void>();
        first.releaseAfter(save);
        assertTrue(locks.isLocked(PET));
        assertTrue(locks.tryAcquire(PET, SECOND).isEmpty());

        save.complete(null);
        assertFalse(locks.isLocked(PET));
        assertTrue(locks.tryAcquire(PET, SECOND).isPresent());
    }

    @Test
    void failedSaveKeepsThePetLockedToPreventAStaleReopen() {
        var locks = new SaddlebagViewLock();
        var lease = locks.tryAcquire(PET, FIRST).orElseThrow();
        var failed = new CompletableFuture<Void>();
        lease.releaseAfter(failed);
        failed.completeExceptionally(new IllegalStateException("database unavailable"));
        assertTrue(locks.isLocked(PET));
        assertTrue(locks.tryAcquire(PET, SECOND).isEmpty());
    }
}
