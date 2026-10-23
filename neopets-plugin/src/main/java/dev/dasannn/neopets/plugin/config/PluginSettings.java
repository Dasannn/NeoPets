package dev.dasannn.neopets.plugin.config;

import dev.dasannn.neopets.core.model.RankLimits;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Immutable typed settings loaded from {@code config.yml}. */
public record PluginSettings(
        int configVersion,
        Storage storage,
        Features features,
        double followTeleportDistance,
        Abilities abilities,
        Map<String, RankLimits> ranks,
        Courier courier,
        Duration playerDataLoadTimeout
) {
    public PluginSettings {
        if (configVersion < 1) {
            throw new IllegalArgumentException("configVersion must be positive");
        }
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(features, "features");
        if (!Double.isFinite(followTeleportDistance) || followTeleportDistance <= 0) {
            throw new IllegalArgumentException("followTeleportDistance must be finite and positive");
        }
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(ranks, "ranks");
        ranks = Map.copyOf(ranks);
        if (!ranks.containsKey("default")) {
            throw new IllegalArgumentException("ranks must contain default");
        }
        Objects.requireNonNull(courier, "courier");
        Objects.requireNonNull(playerDataLoadTimeout, "playerDataLoadTimeout");
        if (playerDataLoadTimeout.isZero() || playerDataLoadTimeout.isNegative()) {
            throw new IllegalArgumentException("playerDataLoadTimeout must be positive");
        }
    }

    /** Supported persistence backends. */
    public enum StorageType {
        SQLITE,
        MYSQL
    }

    /** Persistence selection and settings for both supported backends. */
    public record Storage(StorageType type, String sqliteFile, Mysql mysql) {
        public Storage {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(sqliteFile, "sqliteFile");
            Objects.requireNonNull(mysql, "mysql");
            if (sqliteFile.isBlank()) {
                throw new IllegalArgumentException("sqliteFile must not be blank");
            }
        }
    }

    /** MySQL/MariaDB connection settings. */
    public record Mysql(String host, int port, String database, String username, String password) {
        public Mysql {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            if (host.isBlank() || database.isBlank() || username.isBlank()) {
                throw new IllegalArgumentException("host, database and username must not be blank");
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException("port must be between 1 and 65535");
            }
        }
    }

    /** Feature flags and feature-specific settings. */
    public record Features(Death death, boolean shopEnabled, boolean renameEnabled, Reset reset) {
        public Features {
            Objects.requireNonNull(death, "death");
            Objects.requireNonNull(reset, "reset");
        }
    }

    /** Pet-death feature flags. */
    public record Death(boolean enabled, boolean pvp, boolean keepSkills) {
    }

    /** Player skill-tree reset settings. */
    public record Reset(boolean playerEnabled, BigDecimal cost) {
        public Reset {
            Objects.requireNonNull(cost, "cost");
            if (cost.signum() < 0) {
                throw new IllegalArgumentException("cost must not be negative");
            }
        }
    }

    /** Settings for custom pet abilities. */
    public record Abilities(Zarpazo zarpazo, Vigia vigia) {
        public Abilities {
            Objects.requireNonNull(zarpazo, "zarpazo");
            Objects.requireNonNull(vigia, "vigia");
        }
    }

    /** Polar-bear Zarpazo settings. */
    public record Zarpazo(Duration cooldown, double radius, double damage) {
        public Zarpazo {
            requirePositive(cooldown, "zarpazo cooldown");
            requirePositiveFinite(radius, "zarpazo radius");
            requirePositiveFinite(damage, "zarpazo damage");
        }
    }

    /** Parrot Vigía settings. */
    public record Vigia(Duration cooldown, double radius, Duration duration) {
        public Vigia {
            requirePositive(cooldown, "vigia cooldown");
            requirePositiveFinite(radius, "vigia radius");
            requirePositive(duration, "vigia duration");
        }
    }

    /** Donkey courier timing settings. */
    public record Courier(Duration requestTimeout, Duration cooldown) {
        public Courier {
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            Objects.requireNonNull(cooldown, "cooldown");
            if (requestTimeout.isZero() || requestTimeout.isNegative() || cooldown.isNegative()) {
                throw new IllegalArgumentException("requestTimeout must be positive and cooldown non-negative");
            }
        }
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
