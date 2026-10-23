package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Wolf;

/** Applies dog appearances: required {@code variant} and optional {@code collar-color}. */
public final class WolfVariantApplier implements VariantApplier<Wolf> {
    @Override
    public Class<Wolf> entityClass() {
        return Wolf.class;
    }

    @Override
    public void apply(Wolf entity, VariantAppearance appearance) {
        Objects.requireNonNull(entity, "entity");
        var errors = validate(appearance);
        AppearanceRules.requireValid("dog", errors);

        var properties = appearance.properties();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT);
        var variant = registry.get(NamespacedKey.minecraft(properties.get(AppearanceRules.VARIANT)));
        entity.setVariant(Objects.requireNonNull(variant, "registered wolf variant"));
        var collarColor = properties.get(AppearanceRules.COLLAR_COLOR);
        if (collarColor != null) {
            entity.setCollarColor(DyeColor.valueOf(collarColor.toUpperCase(Locale.ROOT)));
        }
    }

    @Override
    public List<String> validate(VariantAppearance appearance) {
        return AppearanceRules.wolf(appearance);
    }
}
