package dev.dasannn.neopets.core.model;

/** A stable lowercase identifier for a pet species. */
public record PetTypeId(String value) {
    public PetTypeId {
        value = IdentifierValidation.requireLowercaseKey(value, "value");
    }

    @Override
    public String toString() {
        return value;
    }
}
