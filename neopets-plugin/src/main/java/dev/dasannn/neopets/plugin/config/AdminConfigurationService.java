package dev.dasannn.neopets.plugin.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Serializes validated, atomic admin edits and configuration reloads away from the server thread. */
public final class AdminConfigurationService implements AutoCloseable {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private final Path configFile;
    private final ConfigurationLoader validator;
    private final ReloadAction reloadAction;
    private final ExecutorService writer;

    /** Creates the production single-writer service for a plugin data directory. */
    public AdminConfigurationService(
            Path dataDirectory, ConfigurationLoader validator, ConfigurationManager configuration
    ) {
        this(dataDirectory.resolve("config.yml"), validator, configuration::reload,
                Executors.newSingleThreadExecutor(Thread.ofPlatform()
                        .name("NeoPets-config-writer", 0).factory()));
    }

    AdminConfigurationService(
            Path configFile,
            ConfigurationLoader validator,
            ReloadAction reloadAction,
            ExecutorService writer
    ) {
        this.configFile = Objects.requireNonNull(configFile, "configFile");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** Queues one edit, validates the copied YAML, atomically replaces the file, and hot-reloads it. */
    public CompletableFuture<EditResult> edit(
            FeatureRegistry.Feature feature, FeatureRegistry.Interaction interaction
    ) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(interaction, "interaction");
        return CompletableFuture.supplyAsync(() -> {
            try {
                YamlConfiguration source = loadWithComments(configFile);
                YamlConfiguration candidate = copy(source);
                Object previous = candidate.get(feature.path());
                Object replacement = feature.type().apply(previous, interaction);
                candidate.set(feature.path(), replacement);
                validator.validateConfig(candidate);
                replaceAtomically(candidate);
                reloadAction.reload();
                return new EditResult(feature, previous, replacement);
            } catch (IOException | InvalidConfigurationException | ConfigurationLoadException exception) {
                throw new CompletionException(exception);
            }
        }, writer);
    }

    /** Queues the same validated hot-reload path used after an admin edit. */
    public CompletableFuture<Void> reload() {
        return CompletableFuture.runAsync(() -> {
            try {
                reloadAction.reload();
            } catch (IOException | ConfigurationLoadException exception) {
                throw new CompletionException(exception);
            }
        }, writer);
    }

    private static YamlConfiguration loadWithComments(Path path)
            throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        yaml.options().parseComments(true);
        yaml.loadFromString(Files.readString(path, StandardCharsets.UTF_8));
        return yaml;
    }

    private static YamlConfiguration copy(YamlConfiguration source) throws InvalidConfigurationException {
        var copy = new YamlConfiguration();
        copy.options().parseComments(true);
        copy.loadFromString(source.saveToString());
        return copy;
    }

    private void replaceAtomically(YamlConfiguration candidate) throws IOException {
        Path parent = Objects.requireNonNull(configFile.toAbsolutePath().getParent(), "config parent");
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, ".neopets-config-", ".tmp");
        try {
            Files.writeString(temporary, candidate.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("atomic config replacement is not supported", exception);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void close() throws InterruptedException {
        writer.shutdown();
        if (!writer.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            writer.shutdownNow();
        }
    }

    /** Result of one successfully persisted and reloaded edit. */
    public record EditResult(FeatureRegistry.Feature feature, Object previousValue, Object currentValue) {
        public EditResult {
            Objects.requireNonNull(feature, "feature");
            Objects.requireNonNull(previousValue, "previousValue");
            Objects.requireNonNull(currentValue, "currentValue");
        }
    }

    @FunctionalInterface
    interface ReloadAction {
        void reload() throws IOException, ConfigurationLoadException;
    }
}
