package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantAppearance;
import dev.dasannn.neopets.core.model.VariantId;
import dev.dasannn.neopets.core.pet.InMemoryVariantCatalog;
import dev.dasannn.neopets.plugin.config.ConfigurationSnapshot;
import dev.dasannn.neopets.plugin.config.LanguageCatalog;
import dev.dasannn.neopets.plugin.config.PluginSettings;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PetsMenuIconTest {
    @Test
    void usesConfiguredIconBeforeTheTypeSpawnEgg() {
        PetVariant dog = variant("dog");
        ConfigurationSnapshot configured = snapshot(dog, Map.of(dog.id(), "BONE"));
        ConfigurationSnapshot fallback = snapshot(dog, Map.of());

        assertEquals("BONE", PetsMenu.iconMaterial(configured, dog));
        assertEquals("WOLF_SPAWN_EGG", PetsMenu.iconMaterial(fallback, dog));
    }

    @Test
    void hasApprovedSpawnEggFallbackForEveryPetType() {
        assertEquals("WOLF_SPAWN_EGG", icon("dog"));
        assertEquals("CAT_SPAWN_EGG", icon("cat"));
        assertEquals("PARROT_SPAWN_EGG", icon("parrot"));
        assertEquals("POLAR_BEAR_SPAWN_EGG", icon("polar_bear"));
        assertEquals("DONKEY_SPAWN_EGG", icon("donkey"));
    }

    private static String icon(String type) {
        PetVariant variant = variant(type);
        return PetsMenu.iconMaterial(snapshot(variant, Map.of()), variant);
    }

    private static PetVariant variant(String type) {
        return new PetVariant(new VariantId(type + "_preview"), new PetTypeId(type),
                VariantAppearance.empty(), false, 1.0, type);
    }

    private static ConfigurationSnapshot snapshot(PetVariant variant, Map<VariantId, String> icons) {
        var storage = new PluginSettings.Storage(PluginSettings.StorageType.SQLITE, "test.db",
                new PluginSettings.Mysql("localhost", 3306, "test", "test", "test"));
        var features = new PluginSettings.Features(new PluginSettings.Death(true, false, true),
                false, true, new PluginSettings.Reset(false, BigDecimal.ZERO));
        var settings = new PluginSettings(1, storage, features, 20, Map.of("default",
                new dev.dasannn.neopets.core.model.RankLimits(1, 3, 10)),
                new PluginSettings.Courier(Duration.ofMinutes(5), Duration.ofMinutes(15)),
                Duration.ofSeconds(10));
        return new ConfigurationSnapshot(settings, new InMemoryVariantCatalog(java.util.List.of(variant)), icons,
                Map.of(), new LanguageCatalog(Map.of()));
    }
}
