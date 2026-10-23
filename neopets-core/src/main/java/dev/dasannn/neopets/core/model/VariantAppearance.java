package dev.dasannn.neopets.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * Opaque appearance properties interpreted by the platform adapter.
 *
 * <p>The core deliberately attaches no meaning to keys or values.</p>
 */
public record VariantAppearance(Map<String, String> properties) {
    public VariantAppearance {
        Objects.requireNonNull(properties, "properties");
        properties = Map.copyOf(properties);
    }

    /** Returns an appearance with no platform-specific properties. */
    public static VariantAppearance empty() {
        return new VariantAppearance(Map.of());
    }
}
