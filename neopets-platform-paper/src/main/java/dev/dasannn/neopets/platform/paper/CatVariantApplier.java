package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Cat;

/** Applies cat appearances: required {@code type} and optional {@code collar-color}. */
public final class CatVariantApplier implements VariantApplier<Cat> {
    @Override
    public Class<Cat> entityClass() {
        return Cat.class;
    }

    @Override
    public void apply(Cat entity, VariantAppearance appearance) {
        Objects.requireNonNull(entity, "entity");
        var errors = validate(appearance);
        AppearanceRules.requireValid("cat", errors);

        var properties = appearance.properties();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT);
        var type = registry.get(NamespacedKey.minecraft(properties.get(AppearanceRules.TYPE)));
        entity.setCatType(Objects.requireNonNull(type, "registered cat type"));
        var collarColor = properties.get(AppearanceRules.COLLAR_COLOR);
        if (collarColor != null) {
            entity.setCollarColor(DyeColor.valueOf(collarColor.toUpperCase(Locale.ROOT)));
        }
    }

    @Override
    public List<String> validate(VariantAppearance appearance) {
        return AppearanceRules.cat(appearance);
    }
}
