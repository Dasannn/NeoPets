package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.PolarBear;

/** Applies polar-bear appearances; no appearance keys are accepted. */
public final class PolarBearVariantApplier implements VariantApplier<PolarBear> {
    @Override
    public Class<PolarBear> entityClass() {
        return PolarBear.class;
    }

    @Override
    public void apply(PolarBear entity, VariantAppearance appearance) {
        Objects.requireNonNull(entity, "entity");
        var errors = validate(appearance);
        AppearanceRules.requireValid("polar_bear", errors);
    }

    @Override
    public List<String> validate(VariantAppearance appearance) {
        return AppearanceRules.noProperties(appearance);
    }
}
