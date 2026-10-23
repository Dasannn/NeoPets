package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Donkey;

/** Applies donkey appearances; vanilla donkeys expose no coat keys, so none are accepted. */
public final class DonkeyVariantApplier implements VariantApplier<Donkey> {
    @Override
    public Class<Donkey> entityClass() {
        return Donkey.class;
    }

    @Override
    public void apply(Donkey entity, VariantAppearance appearance) {
        Objects.requireNonNull(entity, "entity");
        var errors = validate(appearance);
        AppearanceRules.requireValid("donkey", errors);
    }

    @Override
    public List<String> validate(VariantAppearance appearance) {
        return AppearanceRules.noProperties(appearance);
    }
}
