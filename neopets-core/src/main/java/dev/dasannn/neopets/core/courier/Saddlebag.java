package dev.dasannn.neopets.core.courier;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Arrays;
import java.util.Objects;

/** Versioned opaque serialized contents of one pet's 27-slot saddlebag. */
public record Saddlebag(PetId petId, PlayerId owner, byte[] contents, long version) {
    public Saddlebag {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(contents, "contents");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        contents = Arrays.copyOf(contents, contents.length);
    }

    @Override
    public byte[] contents() {
        return Arrays.copyOf(contents, contents.length);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof Saddlebag other
                && petId.equals(other.petId) && owner.equals(other.owner)
                && version == other.version && Arrays.equals(contents, other.contents);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(petId, owner, version);
        return 31 * result + Arrays.hashCode(contents);
    }
}
