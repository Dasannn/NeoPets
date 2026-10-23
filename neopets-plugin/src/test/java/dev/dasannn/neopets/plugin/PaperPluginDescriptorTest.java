package dev.dasannn.neopets.plugin;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class PaperPluginDescriptorTest {

    @Test
    void builtDescriptorDeclaresPaperEntryPoints() throws IOException {
        try (var descriptor = getClass().getResourceAsStream("/paper-plugin.yml")) {
            assertTrue(descriptor != null, "paper-plugin.yml must be present in processed resources");
            var yaml = new String(descriptor.readAllBytes(), StandardCharsets.UTF_8);

            assertAll(
                    () -> assertTrue(yaml.contains("main: dev.dasannn.neopets.plugin.NeoPetsPlugin")),
                    () -> assertTrue(yaml.contains(
                            "bootstrapper: dev.dasannn.neopets.plugin.bootstrap.NeoPetsBootstrap")),
                    () -> assertTrue(yaml.contains(
                            "loader: dev.dasannn.neopets.plugin.bootstrap.NeoPetsLoader")));
        }
    }
}
