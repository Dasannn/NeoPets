package dev.dasannn.neopets.core.model;

import java.util.Objects;
import java.util.regex.Pattern;

final class IdentifierValidation {
    private static final Pattern LOWERCASE_KEY = Pattern.compile("[a-z0-9]+(?:_[a-z0-9]+)*");

    private IdentifierValidation() {
    }

    static String requireLowercaseKey(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (!LOWERCASE_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a lowercase key");
        }
        return value;
    }
}
