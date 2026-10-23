package dev.dasannn.neopets.core.skilltree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A custom ability identifier with optional opaque parameters for each attribute level. */
public record CustomEffectReference(
        String abilityId,
        Optional<List<Map<String, String>>> parametersPerLevel
) implements EffectReference {
    public CustomEffectReference {
        abilityId = SkillTreeValidation.requireLowercaseKey(abilityId, "abilityId");
        Objects.requireNonNull(parametersPerLevel, "parametersPerLevel");
        parametersPerLevel = parametersPerLevel.map(CustomEffectReference::immutableParameters);
    }

    /** Creates an ability reference that needs no per-level parameters. */
    public CustomEffectReference(String abilityId) {
        this(abilityId, Optional.empty());
    }

    /** Creates an ability reference with per-level parameters. */
    public CustomEffectReference(String abilityId, List<? extends Map<String, String>> parametersPerLevel) {
        this(abilityId, Optional.of(immutableParameters(parametersPerLevel)));
    }

    private static List<Map<String, String>> immutableParameters(List<? extends Map<String, String>> parameters) {
        Objects.requireNonNull(parameters, "parametersPerLevel value");
        return parameters.stream()
                .map(CustomEffectReference::immutableParameterMap)
                .toList();
    }

    private static Map<String, String> immutableParameterMap(Map<String, String> parameters) {
        Objects.requireNonNull(parameters, "per-level parameters");
        return Map.copyOf(new LinkedHashMap<>(parameters));
    }
}
