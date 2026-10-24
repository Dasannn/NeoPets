package dev.dasannn.neopets.platform.paper.ability;

import dev.dasannn.neopets.platform.paper.PetKeys;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Selects hostile ability targets while excluding owners, all players, and NeoPets entities. */
public final class AbilityTargetSelector {
    private AbilityTargetSelector() {
    }

    /**
     * Pure target-selection rule, separated from Bukkit world queries for deterministic testing.
     *
     * @param candidates already range-limited candidates
     * @param ownerId pet owner's entity UUID
     * @return eligible target values in input order
     */
    public static <T> List<T> selectHostiles(Collection<TargetCandidate<T>> candidates, UUID ownerId) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(ownerId, "ownerId");
        var selected = new ArrayList<T>();
        for (TargetCandidate<T> candidate : candidates) {
            Objects.requireNonNull(candidate, "candidate");
            if (candidate.hostile()
                    && !candidate.player()
                    && !candidate.pet()
                    && !candidate.entityId().equals(ownerId)) {
                selected.add(candidate.target());
            }
        }
        return List.copyOf(selected);
    }

    /** Adapts nearby Bukkit entities to the pure selection rule. */
    public static List<LivingEntity> selectHostileEntities(
            Collection<? extends Entity> entities,
            UUID ownerId
    ) {
        Objects.requireNonNull(entities, "entities");
        var candidates = entities.stream()
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .map(entity -> new TargetCandidate<>(
                        entity,
                        entity.getUniqueId(),
                        entity instanceof Enemy || entity instanceof Monster,
                        entity instanceof Player,
                        entity.getPersistentDataContainer().has(PetKeys.PET_ID, PersistentDataType.STRING)))
                .toList();
        return selectHostiles(candidates, ownerId);
    }

    /** Immutable facts used by the target-selection rule. */
    public record TargetCandidate<T>(T target, UUID entityId, boolean hostile, boolean player, boolean pet) {
        public TargetCandidate {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(entityId, "entityId");
        }
    }
}
