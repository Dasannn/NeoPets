package dev.dasannn.neopets.plugin.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdminConfigurationServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void invalidCandidateIsRefusedWithoutWriting() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Path configFile = temporaryDirectory.resolve("config.yml");
        String before = Files.readString(configFile);
        var reloads = new AtomicInteger();
        var invalidPort = new FeatureRegistry.Feature(
                "storage.mysql.port", "STONE", "unused.name", "unused.description",
                new FeatureRegistry.BoundedNumber(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, true),
                Optional.empty(), ignored -> 0);

        try (var service = service(configFile, loader, reloads)) {
            assertThrows(CompletionException.class, () -> service.edit(invalidPort,
                    new FeatureRegistry.Interaction(false, false)).join());
        }

        assertEquals(before, Files.readString(configFile));
        assertEquals(0, reloads.get());
    }

    @Test
    void validEditAtomicallyReplacesCommentedYamlAndInvokesReload() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Path configFile = temporaryDirectory.resolve("config.yml");
        var reloads = new AtomicInteger();

        try (var service = service(configFile, loader, reloads)) {
            service.edit(FeatureRegistry.find("death.enabled").orElseThrow(),
                    new FeatureRegistry.Interaction(true, false)).join();
        }

        var changed = new YamlConfiguration();
        changed.load(configFile.toFile());
        assertFalse(changed.getBoolean("death.enabled"));
        assertEquals(1, reloads.get());
        assertTrue(Files.readString(configFile).contains("# Incremented only when NeoPets changes"));
        try (var files = Files.list(temporaryDirectory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".neopets-config-")));
        }
    }

    @Test
    void concurrentAdminEditsAreSerializedWithoutLosingAnIncrement() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        loader.load();
        Path configFile = temporaryDirectory.resolve("config.yml");
        var reloads = new AtomicInteger();
        FeatureRegistry.Feature cost = FeatureRegistry.find("reset.cost").orElseThrow();

        try (var service = service(configFile, loader, reloads)) {
            var first = service.edit(cost, new FeatureRegistry.Interaction(true, false));
            var second = service.edit(cost, new FeatureRegistry.Interaction(true, false));
            first.join();
            second.join();
        }

        var changed = new YamlConfiguration();
        changed.load(configFile.toFile());
        assertEquals(0, BigDecimal.valueOf(5_200).compareTo(
                new BigDecimal(changed.get("reset.cost").toString())));
        assertEquals(2, reloads.get());
    }

    @Test
    void editsPublishTheRealReloadedSnapshotAndManualReloadUsesTheSameWriter() throws Exception {
        var loader = new ConfigurationLoader(temporaryDirectory);
        var manager = new ConfigurationManager(loader);
        Thread caller = Thread.currentThread();
        var writerThread = new AtomicReference<Thread>();
        var enteredReload = new CountDownLatch(1);
        var releaseReload = new CountDownLatch(1);
        var reloads = new AtomicInteger();
        Path configFile = temporaryDirectory.resolve("config.yml");

        try (var service = new AdminConfigurationService(configFile, loader, () -> {
            writerThread.set(Thread.currentThread());
            if (reloads.incrementAndGet() == 1) {
                enteredReload.countDown();
                try {
                    if (!releaseReload.await(5, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("test did not release reload");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException(exception);
                }
            }
            manager.reload();
        }, Executors.newSingleThreadExecutor())) {
            var edit = service.edit(FeatureRegistry.find("death.enabled").orElseThrow(),
                    new FeatureRegistry.Interaction(true, false));
            try {
                assertTrue(enteredReload.await(5, TimeUnit.SECONDS));
                var manualReload = service.reload();
                var secondEdit = service.edit(FeatureRegistry.find("death.pvp").orElseThrow(),
                        new FeatureRegistry.Interaction(true, false));
                assertFalse(edit.isDone());
                assertFalse(manualReload.isDone());
                assertFalse(secondEdit.isDone());
                assertEquals(1, reloads.get());
                assertFalse(YamlConfiguration.loadConfiguration(configFile.toFile()).getBoolean("death.pvp"));
                releaseReload.countDown();
                edit.get(5, TimeUnit.SECONDS);
                manualReload.get(5, TimeUnit.SECONDS);
                secondEdit.get(5, TimeUnit.SECONDS);
                assertFalse(manager.current().settings().features().death().enabled());
                assertTrue(manager.current().settings().features().death().pvp());
                assertEquals(3, reloads.get());
                assertFalse(caller == writerThread.get());
            } finally {
                releaseReload.countDown();
            }
        }
    }

    private static AdminConfigurationService service(
            Path configFile, ConfigurationLoader loader, AtomicInteger reloads
    ) {
        return new AdminConfigurationService(configFile, loader, reloads::incrementAndGet,
                Executors.newSingleThreadExecutor());
    }
}
