package dev.dasannn.neopets.core.model;

/** A stable lowercase identifier for a configured pet variant. */
public record VariantId(String value) {
    public VariantId {
        value = IdentifierValidation.requireLowercaseKey(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
