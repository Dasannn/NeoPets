package dev.dasannn.neopets.platform.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.VariantAppearance;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppearanceRulesTest {
    @Test
    void acceptsEverySupportedWolfVariantAndDyeColor() {
        for (var variant : java.util.List.of(
                "ashen", "black", "chestnut", "pale", "rusty", "snowy", "spotted", "striped", "woods")) {
            assertTrue(AppearanceRules.wolf(appearance("variant", variant)).isEmpty());
        }
        for (var dye : java.util.List.of(
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
                "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            assertTrue(AppearanceRules.wolf(appearance("variant", "pale", "collar-color", dye)).isEmpty());
            assertTrue(AppearanceRules.cat(appearance("type", "tabby", "collar-color", dye)).isEmpty());
        }
    }

    @Test
    void rejectsMissingAndInvalidWolfProperties() {
        assertEquals(
                java.util.List.of("missing required appearance key 'variant'"),
                AppearanceRules.wolf(VariantAppearance.empty()));
        assertTrue(AppearanceRules.wolf(appearance("variant", "tundra")).getFirst()
                .startsWith("invalid value 'tundra'"));
        assertTrue(AppearanceRules.wolf(appearance("variant", "pale", "collar-color", "teal")).getFirst()
                .startsWith("invalid value 'teal'"));
    }

    @Test
    void validatesCatTypeAndOptionalCollar() {
        for (var type : java.util.List.of(
                "all_black", "black", "british_shorthair", "calico", "jellie", "persian", "ragdoll", "red",
                "siamese", "tabby", "white")) {
            assertTrue(AppearanceRules.cat(appearance("type", type)).isEmpty());
        }
        assertTrue(AppearanceRules.cat(appearance("type", "jellie", "collar-color", "red")).isEmpty());
        assertTrue(AppearanceRules.cat(appearance("type", "tuxedo")).getFirst()
                .startsWith("invalid value 'tuxedo'"));
    }

    @Test
    void validatesAllParrotVariants() {
        for (var variant : java.util.List.of("red", "blue", "green", "cyan", "gray")) {
            assertTrue(AppearanceRules.parrot(appearance("variant", variant)).isEmpty());
        }
        assertTrue(AppearanceRules.parrot(appearance("variant", "yellow")).getFirst()
                .startsWith("invalid value 'yellow'"));
    }

    @Test
    void rejectsUnknownKeysForEverySchema() {
        assertEquals(
                java.util.List.of("unsupported appearance key 'sound'"),
                AppearanceRules.noProperties(appearance("sound", "soft")));
        assertTrue(AppearanceRules.parrot(appearance("variant", "red", "collar-color", "red"))
                .contains("unsupported appearance key 'collar-color'"));
    }

    @Test
    void valuesAreCanonicalLowercaseIdentifiers() {
        assertTrue(AppearanceRules.wolf(appearance("variant", "PALE")).getFirst()
                .startsWith("invalid value 'PALE'"));
        assertTrue(AppearanceRules.cat(appearance("type", "")).getFirst()
                .startsWith("invalid value ''"));
    }

    @Test
    void polarBearAndDonkeyAcceptEmptyAppearance() {
        assertTrue(AppearanceRules.noProperties(VariantAppearance.empty()).isEmpty());
    }

    private static VariantAppearance appearance(String... entries) {
        var properties = new java.util.HashMap<String, String>();
        for (var index = 0; index < entries.length; index += 2) {
            properties.put(entries[index], entries[index + 1]);
        }
        return new VariantAppearance(Map.copyOf(properties));
    }
}
