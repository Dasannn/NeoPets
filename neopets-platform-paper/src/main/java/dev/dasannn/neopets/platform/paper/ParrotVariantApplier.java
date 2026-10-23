package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.entity.Parrot;

/** Applies parrot appearances using the required {@code variant} key. */
public final class ParrotVariantApplier implements VariantApplier<Parrot> {
    @Override
    public Class<Parrot> entityClass() {
        return Parrot.class;
    }

    @Override
    public void apply(Parrot entity, VariantAppearance appearance) {
        Objects.requireNonNull(entity, "entity");
        var errors = validate(appearance);
        AppearanceRules.requireValid("parrot", errors);
        var value = appearance.properties().get(AppearanceRules.VARIANT);
        entity.setVariant(Parrot.Variant.valueOf(value.toUpperCase(Locale.ROOT)));
    }

    @Override
    public List<String> validate(VariantAppearance appearance) {
        return AppearanceRules.parrot(appearance);
    }
}
