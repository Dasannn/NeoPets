package dev.dasannn.neopets.plugin.integration;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Resolves NeoPets placeholder identifiers from non-blocking immutable snapshots. */
public final class NeoPetsPlaceholders {
    private final PlaceholderDataSource dataSource;
    private final Supplier<String> emptyActivePetValue;

    /** Creates a resolver backed by a snapshot source and a live language-value supplier. */
    public NeoPetsPlaceholders(
            PlaceholderDataSource dataSource,
            Supplier<String> emptyActivePetValue
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.emptyActivePetValue = Objects.requireNonNull(emptyActivePetValue, "emptyActivePetValue");
    }

    /**
     * Resolves one identifier, returning {@code null} for an unknown identifier and an empty string
     * when the player is absent or unloaded.
     */
    public String resolve(UUID playerId, String identifier) {
        Objects.requireNonNull(identifier, "identifier");
        if (!isKnown(identifier)) {
            return null;
        }
        if (playerId == null) {
            return "";
        }
        return dataSource.snapshot(playerId)
                .map(snapshot -> resolve(snapshot, identifier))
                .orElse("");
    }

    private String resolve(PlaceholderSnapshot snapshot, String identifier) {
        return switch (identifier) {
            case "sp" -> Integer.toString(snapshot.skillPoints());
            case "sp_max" -> Integer.toString(snapshot.maxSkillPointsPerPet());
            case "pets_owned" -> Integer.toString(snapshot.ownedPets());
            case "pets_active" -> Integer.toString(snapshot.activePets());
            case "max_active" -> Integer.toString(snapshot.maxActivePets());
            case "active_pet_name" -> snapshot.firstActivePetName()
                    .orElseGet(() -> Objects.requireNonNull(
                            emptyActivePetValue.get(), "emptyActivePetValue result"));
            default -> throw new IllegalStateException("known placeholder was not resolved: " + identifier);
        };
    }

    private static boolean isKnown(String identifier) {
        return switch (identifier) {
            case "sp", "sp_max", "pets_owned", "pets_active", "max_active", "active_pet_name" -> true;
            default -> false;
        };
    }
}
