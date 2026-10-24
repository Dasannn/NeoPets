package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class AppearanceRules {
    static final String VARIANT = "variant";
    static final String TYPE = "type";
    static final String COLLAR_COLOR = "collar-color";

    private static final Set<String> WOLF_VARIANTS = Set.of(
            "ashen", "black", "chestnut", "pale", "rusty", "snowy", "spotted", "striped", "woods");
    private static final Set<String> CAT_TYPES = Set.of(
            "all_black", "black", "british_shorthair", "calico", "jellie", "persian", "ragdoll", "red",
            "siamese", "tabby", "white");
    private static final Set<String> PARROT_VARIANTS = Set.of("red", "blue", "green", "cyan", "gray");
    private static final Set<String> DYE_COLORS = Set.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black");

    private AppearanceRules() {
    }

    static List<String> wolf(VariantAppearance appearance) {
        return validate(appearance, Set.of(VARIANT, COLLAR_COLOR), Map.of(VARIANT, WOLF_VARIANTS),
                Map.of(COLLAR_COLOR, DYE_COLORS));
    }

    static List<String> cat(VariantAppearance appearance) {
        return validate(appearance, Set.of(TYPE, COLLAR_COLOR), Map.of(TYPE, CAT_TYPES),
                Map.of(COLLAR_COLOR, DYE_COLORS));
    }

    static List<String> parrot(VariantAppearance appearance) {
        return validate(appearance, Set.of(VARIANT), Map.of(VARIANT, PARROT_VARIANTS), Map.of());
    }

    static List<String> noProperties(VariantAppearance appearance) {
        return validate(appearance, Set.of(), Map.of(), Map.of());
    }

    static void requireValid(String petType, List<String> errors) {
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid " + petType + " appearance: " + String.join("; ", errors));
        }
    }

    private static List<String> validate(
            VariantAppearance appearance,
            Set<String> acceptedKeys,
            Map<String, Set<String>> requiredValues,
            Map<String, Set<String>> optionalValues
    ) {
        Objects.requireNonNull(appearance, "appearance");
        var properties = appearance.properties();
        var errors = new ArrayList<String>();

        for (var requiredKey : new TreeSet<>(requiredValues.keySet())) {
            if (!properties.containsKey(requiredKey)) {
                errors.add("missing required appearance key '" + requiredKey + "'");
            }
        }
        for (var key : new TreeSet<>(properties.keySet())) {
            if (!acceptedKeys.contains(key)) {
                errors.add("unsupported appearance key '" + key + "'");
                continue;
            }
            var allowedValues = requiredValues.getOrDefault(key, optionalValues.get(key));
            var value = properties.get(key);
            if (!allowedValues.contains(value)) {
                errors.add("invalid value '" + value + "' for appearance key '" + key
                        + "' (expected one of: " + String.join(", ", new TreeSet<>(allowedValues)) + ")");
            }
        }
        return List.copyOf(errors);
    }
}
