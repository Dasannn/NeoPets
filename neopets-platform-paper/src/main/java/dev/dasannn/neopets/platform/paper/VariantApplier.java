package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.List;
import org.bukkit.entity.Entity;

/**
 * Applies the platform-specific appearance of one pet entity type.
 *
 * @param <E> Bukkit entity type handled by this applier
 */
public interface VariantApplier<E extends Entity> {
    /** Returns the concrete Bukkit entity class to spawn. */
    Class<E> entityClass();

    /** Applies a previously validated appearance to an entity. */
    void apply(E entity, VariantAppearance appearance);

    /**
     * Validates all appearance keys and values without requiring a running server.
     *
     * @return immutable human-readable validation errors, or an empty list when valid
     */
    List<String> validate(VariantAppearance appearance);
}
