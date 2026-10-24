package dev.dasannn.neopets.plugin.config;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.VariantCatalog;
import dev.dasannn.neopets.core.skilltree.SkillTreeDefinition;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Complete immutable configuration state published by a successful load. */
public record ConfigurationSnapshot(
        PluginSettings settings,
        VariantCatalog variants,
        Map<VariantId, String> variantIcons,
        Map<PetTypeId, SkillTreeDefinition> skillTrees,
        LanguageCatalog language,
        UpdaterSettings updater
) {
    public ConfigurationSnapshot(PluginSettings settings, VariantCatalog variants,
            Map<VariantId, String> icons, Map<PetTypeId, SkillTreeDefinition> trees, LanguageCatalog language) {
        this(settings, variants, icons, trees, language, new UpdaterSettings(true, "Dasannn/NeoPets", "", false));
    }

    public ConfigurationSnapshot {
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(variants, "variants");
        Objects.requireNonNull(variantIcons, "variantIcons");
        Objects.requireNonNull(skillTrees, "skillTrees");
        Objects.requireNonNull(language, "language");
        variantIcons = Map.copyOf(variantIcons);
        skillTrees = Map.copyOf(skillTrees);
    }

    /** Finds the configured tree for a pet type. */
    public Optional<SkillTreeDefinition> skillTree(PetTypeId typeId) {
        return Optional.ofNullable(skillTrees.get(Objects.requireNonNull(typeId, "typeId")));
    }
}
