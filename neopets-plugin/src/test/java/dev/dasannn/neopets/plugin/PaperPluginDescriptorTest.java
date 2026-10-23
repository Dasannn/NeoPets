package dev.dasannn.neopets.plugin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class PaperPluginDescriptorTest {

    @Test
    void builtDescriptorDeclaresPaperMetadata() throws IOException {
        try (var descriptor = requiredResource("/paper-plugin.yml")) {
            var yaml = new String(descriptor.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            var version = requiredSystemProperty("neopets.test.version");

            assertAll(
                    () -> assertTrue(yaml.contains("name: NeoPets")),
                    () -> assertTrue(yaml.contains("version: '" + version + "'")),
                    () -> assertTrue(yaml.contains("main: dev.dasannn.neopets.plugin.NeoPetsPlugin")),
                    () -> assertTrue(yaml.contains(
                            "bootstrapper: dev.dasannn.neopets.plugin.bootstrap.NeoPetsBootstrap")),
                    () -> assertTrue(yaml.contains(
                            "loader: dev.dasannn.neopets.plugin.bootstrap.NeoPetsLoader")),
                    () -> assertTrue(yaml.contains("api-version: '26.3'")),
                    () -> assertOptionalDependency(yaml, "Vault"),
                    () -> assertOptionalDependency(yaml, "LuckPerms"),
                    () -> assertOptionalDependency(yaml, "PlaceholderAPI"),
                    () -> assertFalse(yaml.contains("${"), "descriptor contains an unexpanded token"));
        }
    }

    @Test
    void runtimeLibraryCoordinatesComeFromTheVersionCatalog() throws IOException {
        var expected = Map.of(
                "hikaricp", requiredSystemProperty("neopets.test.library.hikaricp"),
                "mysql-connector-j", requiredSystemProperty("neopets.test.library.mysql-connector-j"),
                "sqlite-jdbc", requiredSystemProperty("neopets.test.library.sqlite-jdbc"),
                "flyway-core", requiredSystemProperty("neopets.test.library.flyway-core"),
                "flyway-mysql", requiredSystemProperty("neopets.test.library.flyway-mysql"));

        try (var resource = requiredResource("/runtime-libraries.properties")) {
            var libraries = new Properties();
            libraries.load(resource);

            assertEquals(expected.keySet(), libraries.stringPropertyNames());
            expected.forEach((name, coordinate) -> assertEquals(coordinate, libraries.getProperty(name)));
        }
    }

    @Test
    void distributableJarContainsPluginAndProjectClassesOnly() throws IOException {
        var version = requiredSystemProperty("neopets.test.version");
        var jarPath = Path.of(requiredSystemProperty("neopets.test.jar"));

        assertEquals("NeoPets-" + version + ".jar", jarPath.getFileName().toString());
        try (var jar = new JarFile(jarPath.toFile())) {
            var entries = jar.stream().map(entry -> entry.getName()).collect(Collectors.toSet());

            assertAll(
                    () -> assertJarEntries(entries,
                            "paper-plugin.yml",
                            "runtime-libraries.properties",
                            "dev/dasannn/neopets/plugin/NeoPetsPlugin.class",
                            "dev/dasannn/neopets/plugin/bootstrap/NeoPetsBootstrap.class",
                            "dev/dasannn/neopets/plugin/bootstrap/NeoPetsLoader.class"),
                    () -> assertBundledProjectClasses(entries, version),
                    () -> assertFalse(hasEntryUnder(entries, "com/zaxxer/hikari/")),
                    () -> assertFalse(hasEntryUnder(entries, "com/mysql/")),
                    () -> assertFalse(hasEntryUnder(entries, "org/sqlite/")),
                    () -> assertFalse(hasEntryUnder(entries, "org/flywaydb/")),
                    () -> assertFalse(hasEntryUnder(entries, "me/clip/placeholderapi/")));
        }
    }

    private static void assertOptionalDependency(String yaml, String pluginName) {
        assertTrue(yaml.contains("    " + pluginName + ":\n      load: BEFORE\n      required: false"));
    }

    private static void assertJarEntries(Set<String> entries, String... expectedEntries) {
        assertAll(Arrays.stream(expectedEntries)
                .map(expected -> () -> assertTrue(entries.contains(expected), "missing JAR entry: " + expected)));
    }

    private static void assertBundledProjectClasses(Set<String> pluginEntries, String version) throws IOException {
        var bundledJars = Arrays.stream(requiredSystemProperty("neopets.test.bundledJars")
                        .split(Pattern.quote(File.pathSeparator)))
                .map(Path::of)
                .toList();
        var expectedJarNames = Set.of(
                "neopets-api-" + version + ".jar",
                "neopets-core-" + version + ".jar",
                "neopets-storage-" + version + ".jar",
                "neopets-platform-paper-" + version + ".jar");

        assertEquals(expectedJarNames, bundledJars.stream()
                .map(path -> path.getFileName().toString())
                .collect(Collectors.toSet()));
        for (var bundledJar : bundledJars) {
            try (var jar = new JarFile(bundledJar.toFile())) {
                jar.stream()
                        .filter(entry -> entry.getName().endsWith(".class"))
                        .forEach(entry -> assertTrue(
                                pluginEntries.contains(entry.getName()),
                                () -> bundledJar.getFileName() + " class missing from plugin JAR: " + entry.getName()));
            }
        }
    }

    private static boolean hasEntryUnder(Set<String> entries, String prefix) {
        return entries.stream().anyMatch(entry -> entry.startsWith(prefix));
    }

    private InputStream requiredResource(String name) {
        var resource = getClass().getResourceAsStream(name);
        assertNotNull(resource, name + " must be present in processed resources");
        return resource;
    }

    private static String requiredSystemProperty(String name) {
        var value = System.getProperty(name);
        assertNotNull(value, name + " must be supplied by Gradle");
        return value;
    }
}
