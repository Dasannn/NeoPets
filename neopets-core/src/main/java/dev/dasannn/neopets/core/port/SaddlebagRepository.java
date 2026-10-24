package dev.dasannn.neopets.core.port;

import dev.dasannn.neopets.core.courier.Saddlebag;
import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.concurrent.CompletableFuture;

/** Asynchronous optimistic persistence for courier saddlebag contents. */
public interface SaddlebagRepository {
    /** Loads the row, atomically creating version zero with the supplied empty representation when absent. */
    CompletableFuture<Saddlebag> loadOrCreate(PetId petId, PlayerId owner, byte[] emptyContents);

    /** Saves only when {@code expectedVersion} still matches and returns the incremented snapshot. */
    CompletableFuture<Saddlebag> save(
            PetId petId, PlayerId owner, byte[] contents, long expectedVersion
    );
}
