package dev.dasannn.neopets.plugin.gui;

import java.util.Optional;

/** Type filter order used by the pets menu. */
public enum PetFilter {
    ALL(null, "gui.pets.filter-all"),
    DOG("dog", "gui.pets.type-dog"),
    CAT("cat", "gui.pets.type-cat"),
    PARROT("parrot", "gui.pets.type-parrot"),
    POLAR_BEAR("polar_bear", "gui.pets.type-polar-bear"),
    DONKEY("donkey", "gui.pets.type-donkey");

    private final String typeId;
    private final String languageKey;

    PetFilter(String typeId, String languageKey) {
        this.typeId = typeId;
        this.languageKey = languageKey;
    }

    /** Returns the next filter, wrapping back to all types. */
    public PetFilter next() {
        PetFilter[] filters = values();
        return filters[(ordinal() + 1) % filters.length];
    }

    /** Returns whether a pet type belongs in this filter. */
    public boolean includes(String candidateTypeId) {
        return typeId == null || typeId.equals(candidateTypeId);
    }

    public Optional<String> typeId() {
        return Optional.ofNullable(typeId);
    }

    public String languageKey() {
        return languageKey;
    }
}
