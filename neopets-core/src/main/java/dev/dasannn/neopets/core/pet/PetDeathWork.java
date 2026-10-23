package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.time.Instant;
import java.util.Objects;

/** Durable checkpoints for completing all side effects of one pet death. */
public record PetDeathWork(
        PetId petId,
        PlayerId ownerId,
        boolean keepSkills,
        boolean skillsHandled,
        Instant createdAt
) {
    public PetDeathWork {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    /** Returns a copy recording that retained or reset skill progress has been handled. */
    public PetDeathWork withSkillsHandled() {
        return new PetDeathWork(petId, ownerId, keepSkills, true, createdAt);
    }
}
