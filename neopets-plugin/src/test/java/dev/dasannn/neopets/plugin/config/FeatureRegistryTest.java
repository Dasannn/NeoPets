package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeatureRegistryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void containsEveryAdminEditableBranchSettingWithDistinctIcons() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        var snapshot = loader.load();
        List<String> expectedPaths = List.of(
                "death.enabled", "death.pvp", "death.keep-skills", "shop.enabled", "rename.enabled",
                "reset.player-enabled", "reset.cost", "courier.request-timeout-seconds",
                "courier.cooldown-seconds", "abilities.zarpazo.cooldown-seconds",
                "abilities.zarpazo.radius", "abilities.zarpazo.damage",
                "abilities.vigia.cooldown-seconds", "abilities.vigia.radius",
                "abilities.vigia.duration-seconds");

        assertEquals(expectedPaths, FeatureRegistry.features().stream()
                .map(FeatureRegistry.Feature::path).toList());
        assertEquals(FeatureRegistry.features().size(), new HashSet<>(FeatureRegistry.features().stream()
                .map(FeatureRegistry.Feature::iconMaterial).toList()).size());
        FeatureRegistry.features().forEach(feature -> feature.currentValue(snapshot.settings()));
    }

    @Test
    void everyRegistryBoundIsValidatedByTheProductionLoader() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Path configFile = temporaryDirectory.resolve("config.yml");

        for (FeatureRegistry.Feature feature : FeatureRegistry.features()) {
            var belowMinimum = new YamlConfiguration();
            belowMinimum.load(configFile.toFile());
            if (feature.type() instanceof FeatureRegistry.BooleanToggle) {
                belowMinimum.set(feature.path(), "not-a-boolean");
                assertIssue(assertThrows(ConfigurationLoadException.class,
                        () -> loader.validateConfig(belowMinimum)), feature.path());
                continue;
            }

            var number = assertInstanceOf(FeatureRegistry.BoundedNumber.class, feature.type());
            belowMinimum.set(feature.path(), number.minimum().subtract(number.step()));
            assertIssue(assertThrows(ConfigurationLoadException.class,
                    () -> loader.validateConfig(belowMinimum)), feature.path());

            var aboveMaximum = new YamlConfiguration();
            aboveMaximum.load(configFile.toFile());
            aboveMaximum.set(feature.path(), number.maximum().add(number.step()));
            assertIssue(assertThrows(ConfigurationLoadException.class,
                    () -> loader.validateConfig(aboveMaximum)), feature.path());
        }
    }

    @Test
    void toggleAndNumberClicksUseStepsAccelerationAndInclusiveBounds() {
        FeatureRegistry.Feature death = FeatureRegistry.find("death.enabled").orElseThrow();
        assertEquals(false, death.type().apply(true, new FeatureRegistry.Interaction(true, false)));
        assertEquals(true, death.type().apply(false, new FeatureRegistry.Interaction(false, true)));

        var radius = assertInstanceOf(FeatureRegistry.BoundedNumber.class,
                FeatureRegistry.find("abilities.vigia.radius").orElseThrow().type());
        assertEquals(16.5, radius.apply(16.0, new FeatureRegistry.Interaction(true, false)));
        assertEquals(11.0, radius.apply(16.0, new FeatureRegistry.Interaction(false, true)));
        assertEquals(Double.MIN_VALUE, radius.apply(0.1, new FeatureRegistry.Interaction(false, true)));
        assertEquals(128.0, radius.apply(127.9, new FeatureRegistry.Interaction(true, true)));

        var timeout = assertInstanceOf(FeatureRegistry.BoundedNumber.class,
                FeatureRegistry.find("courier.request-timeout-seconds").orElseThrow().type());
        assertEquals(330L, timeout.apply(300, new FeatureRegistry.Interaction(true, false)));
        assertEquals(1L, timeout.apply(10, new FeatureRegistry.Interaction(false, true)));
        assertEquals("30", timeout.format(BigDecimal.valueOf(30)));
    }

    @Test
    void registryEndpointsRoundTripThroughYamlAndTheLoader() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Path configFile = temporaryDirectory.resolve("config.yml");
        for (var feature : FeatureRegistry.features()) {
            if (!(feature.type() instanceof FeatureRegistry.BoundedNumber number)) {
                continue;
            }
            for (boolean increase : List.of(false, true)) {
                var yaml = YamlConfiguration.loadConfiguration(configFile.toFile());
                Object endpoint = number.apply(increase ? number.maximum() : number.minimum(),
                        new FeatureRegistry.Interaction(increase, true));
                yaml.set(feature.path(), endpoint);
                var reloaded = new YamlConfiguration();
                reloaded.loadFromString(yaml.saveToString());
                loader.validateConfig(reloaded);
            }
        }
    }

    @Test
    void optionalFeaturePermissionsAreChecked() {
        var feature = new FeatureRegistry.Feature("death.enabled", "STONE", "name", "description",
                FeatureRegistry.BooleanToggle.INSTANCE, java.util.Optional.of("neopets.admin.death"),
                settings -> settings.features().death().enabled());
        org.junit.jupiter.api.Assertions.assertFalse(feature.isVisibleTo(permission -> false));
        assertTrue(feature.isVisibleTo(permission -> permission.equals("neopets.admin.death")));
    }

    private static void assertIssue(ConfigurationLoadException failure, String path) {
        assertTrue(failure.issues().stream().anyMatch(issue -> issue.path().equals(path)),
                () -> "No validation issue for " + path + ": " + failure.getMessage());
    }
}
