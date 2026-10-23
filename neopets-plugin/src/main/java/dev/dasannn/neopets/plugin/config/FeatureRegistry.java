package dev.dasannn.neopets.plugin.config;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Declarative catalog of the settings exposed by the administration menu. */
public final class FeatureRegistry {
    /** Permission required to open the administration menu. */
    public static final String GUI_PERMISSION = "neopets.admin.gui";

    /** Largest finite decimal accepted by the configuration loader. */
    public static final BigDecimal MAX_FINITE_DECIMAL = new BigDecimal(Double.toString(Double.MAX_VALUE));
    /** Largest duration in seconds representable by {@link java.time.Duration#ofSeconds(long)}. */
    public static final BigDecimal MAX_DURATION_SECONDS = BigDecimal.valueOf(Long.MAX_VALUE);

    private static final List<Feature> FEATURES = List.of(
            toggle("death.enabled", "TOTEM_OF_UNDYING", "death-enabled",
                    settings -> settings.features().death().enabled()),
            toggle("death.pvp", "DIAMOND_SWORD", "death-pvp",
                    settings -> settings.features().death().pvp()),
            toggle("death.keep-skills", "EXPERIENCE_BOTTLE", "death-keep-skills",
                    settings -> settings.features().death().keepSkills()),
            toggle("shop.enabled", "EMERALD", "shop-enabled",
                    settings -> settings.features().shopEnabled()),
            toggle("rename.enabled", "NAME_TAG", "rename-enabled",
                    settings -> settings.features().renameEnabled()),
            toggle("reset.player-enabled", "WATER_BUCKET", "reset-player-enabled",
                    settings -> settings.features().reset().playerEnabled()),
            number("reset.cost", "GOLD_INGOT", "reset-cost", 0, MAX_FINITE_DECIMAL,
                    100, false, settings -> settings.features().reset().cost()),
            number("courier.request-timeout-seconds", "CLOCK", "courier-request-timeout", 1,
                    MAX_DURATION_SECONDS, 30, true,
                    settings -> BigDecimal.valueOf(settings.courier().requestTimeout().toSeconds())),
            number("courier.cooldown-seconds", "RECOVERY_COMPASS", "courier-cooldown", 0,
                    MAX_DURATION_SECONDS, 60, true,
                    settings -> BigDecimal.valueOf(settings.courier().cooldown().toSeconds())),
            toggle("abilities.zarpazo.enabled", "POLAR_BEAR_SPAWN_EGG", "zarpazo-enabled",
                    settings -> settings.abilities().zarpazo().enabled()),
            number("abilities.zarpazo.cooldown-seconds", "IRON_AXE", "zarpazo-cooldown", 1,
                    86_400, 1, true,
                    settings -> BigDecimal.valueOf(settings.abilities().zarpazo().cooldown().toSeconds())),
            positiveNumber("abilities.zarpazo.radius", "TARGET", "zarpazo-radius",
                    128, 0.5,
                    settings -> BigDecimal.valueOf(settings.abilities().zarpazo().radius())),
            positiveNumber("abilities.zarpazo.damage", "BLAZE_POWDER", "zarpazo-damage",
                    2_048, 0.5,
                    settings -> BigDecimal.valueOf(settings.abilities().zarpazo().damage())),
            toggle("abilities.vigia.enabled", "PARROT_SPAWN_EGG", "vigia-enabled",
                    settings -> settings.abilities().vigia().enabled()),
            number("abilities.vigia.cooldown-seconds", "GOAT_HORN", "vigia-cooldown", 1,
                    86_400, 1, true,
                    settings -> BigDecimal.valueOf(settings.abilities().vigia().cooldown().toSeconds())),
            positiveNumber("abilities.vigia.radius", "SPYGLASS", "vigia-radius",
                    128, 0.5,
                    settings -> BigDecimal.valueOf(settings.abilities().vigia().radius())),
            number("abilities.vigia.duration-seconds", "GLOW_INK_SAC", "vigia-duration", 1,
                    3_600, 1, true,
                    settings -> BigDecimal.valueOf(settings.abilities().vigia().duration().toSeconds())),
            toggle("abilities.buscatesoros.enabled", "WOLF_SPAWN_EGG", "buscatesoros-enabled",
                    settings -> settings.abilities().buscatesoros().enabled()),
            number("abilities.buscatesoros.cooldown-seconds", "COMPASS", "buscatesoros-cooldown", 1,
                    86_400, 1, true,
                    settings -> BigDecimal.valueOf(settings.abilities().buscatesoros().cooldown().toSeconds())),
            positiveNumber("abilities.buscatesoros.base-radius", "ENDER_EYE", "buscatesoros-radius",
                    128, 0.5, settings -> BigDecimal.valueOf(settings.abilities().buscatesoros().baseRadius())),
            number("abilities.buscatesoros.duration-seconds", "LANTERN", "buscatesoros-duration", 1,
                    3_600, 1, true,
                    settings -> BigDecimal.valueOf(settings.abilities().buscatesoros().duration().toSeconds())),
            number("abilities.buscatesoros.max-targets", "GOLD_NUGGET", "buscatesoros-max-targets", 1,
                    512, 1, true, settings -> settings.abilities().buscatesoros().maxTargets()),
            number("abilities.buscatesoros.max-scan-blocks", "DIAMOND_ORE", "buscatesoros-max-scan-blocks", 1,
                    262_144, 512, true, settings -> settings.abilities().buscatesoros().maxScanBlocks()),
            toggle("abilities.aterrizaje-felino.enabled", "CAT_SPAWN_EGG", "aterrizaje-felino-enabled",
                    settings -> settings.abilities().felineLandingEnabled()));

    static {
        var paths = new HashSet<String>();
        var icons = new HashSet<String>();
        for (Feature feature : FEATURES) {
            if (!paths.add(feature.path())) {
                throw new IllegalStateException("Duplicate admin feature path: " + feature.path());
            }
            if (!icons.add(feature.iconMaterial())) {
                throw new IllegalStateException("Duplicate admin feature icon: " + feature.iconMaterial());
            }
        }
    }

    private FeatureRegistry() {
    }

    /** Returns every editable setting in stable menu order. */
    public static List<Feature> features() {
        return FEATURES;
    }

    /** Finds an editable setting by its exact YAML path. */
    public static Optional<Feature> find(String path) {
        Objects.requireNonNull(path, "path");
        return FEATURES.stream().filter(feature -> feature.path().equals(path)).findFirst();
    }

    private static Feature toggle(
            String path, String icon, String languageId, Function<PluginSettings, ?> value
    ) {
        return feature(path, icon, languageId, BooleanToggle.INSTANCE, value);
    }

    private static Feature positiveNumber(
            String path, String icon, String languageId, double maximum, double step,
            Function<PluginSettings, ?> value
    ) {
        return feature(path, icon, languageId, new BoundedNumber(BigDecimal.ZERO,
                BigDecimal.valueOf(maximum), BigDecimal.valueOf(step), false, false), value);
    }

    private static Feature number(
            String path,
            String icon,
            String languageId,
            double minimum,
            double maximum,
            double step,
            boolean integral,
            Function<PluginSettings, ?> value
    ) {
        return number(path, icon, languageId, BigDecimal.valueOf(minimum), BigDecimal.valueOf(maximum),
                BigDecimal.valueOf(step), integral, value);
    }

    private static Feature number(
            String path,
            String icon,
            String languageId,
            double minimum,
            BigDecimal maximum,
            double step,
            boolean integral,
            Function<PluginSettings, ?> value
    ) {
        return number(path, icon, languageId, BigDecimal.valueOf(minimum), maximum,
                BigDecimal.valueOf(step), integral, value);
    }

    private static Feature number(
            String path,
            String icon,
            String languageId,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal step,
            boolean integral,
            Function<PluginSettings, ?> value
    ) {
        return feature(path, icon, languageId,
                new BoundedNumber(minimum, maximum, step, integral), value);
    }

    private static Feature feature(
            String path,
            String icon,
            String languageId,
            ValueType type,
            Function<PluginSettings, ?> value
    ) {
        return new Feature(path, icon, "gui.admin.features." + languageId + ".name",
                "gui.admin.features." + languageId + ".description", type, Optional.empty(), value);
    }

    /** One editable config path and all metadata needed to render and mutate it. */
    public static final class Feature {
        private final String path;
        private final String iconMaterial;
        private final String nameKey;
        private final String descriptionKey;
        private final ValueType type;
        private final Optional<String> requiredPermission;
        private final Function<PluginSettings, ?> value;

        public Feature(
                String path,
                String iconMaterial,
                String nameKey,
                String descriptionKey,
                ValueType type,
                Optional<String> requiredPermission,
                Function<PluginSettings, ?> value
        ) {
            this.path = requireText(path, "path");
            this.iconMaterial = requireText(iconMaterial, "iconMaterial");
            this.nameKey = requireText(nameKey, "nameKey");
            this.descriptionKey = requireText(descriptionKey, "descriptionKey");
            this.type = Objects.requireNonNull(type, "type");
            this.requiredPermission = Objects.requireNonNull(requiredPermission, "requiredPermission")
                    .map(permission -> requireText(permission, "requiredPermission"));
            this.value = Objects.requireNonNull(value, "value");
        }

        public String path() {
            return path;
        }

        public String iconMaterial() {
            return iconMaterial;
        }

        public String nameKey() {
            return nameKey;
        }

        public String descriptionKey() {
            return descriptionKey;
        }

        public ValueType type() {
            return type;
        }

        public Optional<String> requiredPermission() {
            return requiredPermission;
        }

        /** Reads this feature's value from an immutable live settings snapshot. */
        public Object currentValue(PluginSettings settings) {
            return Objects.requireNonNull(value.apply(Objects.requireNonNull(settings, "settings")),
                    "feature value");
        }

        /** Reports whether a viewer satisfies this feature's optional additional permission. */
        public boolean isVisibleTo(Predicate<String> permissionCheck) {
            Objects.requireNonNull(permissionCheck, "permissionCheck");
            return requiredPermission.isEmpty() || permissionCheck.test(requiredPermission.orElseThrow());
        }
    }

    /** Supported value kinds and their click-to-value rules. */
    public sealed interface ValueType permits BooleanToggle, BoundedNumber {
        /** Computes the replacement value for an interaction. */
        Object apply(Object currentValue, Interaction interaction);
    }

    /** Boolean feature toggle. */
    public enum BooleanToggle implements ValueType {
        INSTANCE;

        @Override
        public Object apply(Object currentValue, Interaction interaction) {
            Objects.requireNonNull(interaction, "interaction");
            if (!(currentValue instanceof Boolean value)) {
                throw new IllegalArgumentException("current value must be boolean");
            }
            return !value;
        }
    }

    /** Numeric feature constrained to the same range used by the loader. */
    public record BoundedNumber(
            BigDecimal minimum, BigDecimal maximum, BigDecimal step, boolean integral, boolean minimumInclusive
    )
            implements ValueType {
        /** Creates a number with inclusive bounds. */
        public BoundedNumber(BigDecimal minimum, BigDecimal maximum, BigDecimal step, boolean integral) {
            this(minimum, maximum, step, integral, true);
        }

        public BoundedNumber {
            Objects.requireNonNull(minimum, "minimum");
            Objects.requireNonNull(maximum, "maximum");
            Objects.requireNonNull(step, "step");
            minimum = minimum.stripTrailingZeros();
            maximum = maximum.stripTrailingZeros();
            step = step.stripTrailingZeros();
            if (minimum.compareTo(maximum) > 0 || step.signum() <= 0
                    || !minimumInclusive && (integral || minimum.signum() != 0 || maximum.signum() <= 0)) {
                throw new IllegalArgumentException("number bounds and step are invalid");
            }
            if (integral && (minimum.scale() > 0 || maximum.scale() > 0 || step.scale() > 0)) {
                throw new IllegalArgumentException("integral bounds and step must be integers");
            }
        }

        @Override
        public Object apply(Object currentValue, Interaction interaction) {
            Objects.requireNonNull(interaction, "interaction");
            if (!(currentValue instanceof Number number)) {
                throw new IllegalArgumentException("current value must be numeric");
            }
            BigDecimal current;
            try {
                current = new BigDecimal(number.toString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("current value must be finite", exception);
            }
            BigDecimal delta = step.multiply(interaction.accelerated() ? BigDecimal.TEN : BigDecimal.ONE);
            BigDecimal changed = interaction.increase() ? current.add(delta) : current.subtract(delta);
            BigDecimal clamped = changed.max(minimum).min(maximum).stripTrailingZeros();
            if (integral) {
                return clamped.longValueExact();
            }
            // The loader accepts every finite positive double for ability radii/damage.
            return !minimumInclusive && clamped.signum() == 0 ? Double.MIN_VALUE : clamped.doubleValue();
        }

        /** Formats a value without insignificant decimal zeroes. */
        public String format(Number value) {
            Objects.requireNonNull(value, "value");
            BigDecimal normalized = new BigDecimal(value.toString()).stripTrailingZeros();
            return normalized.toPlainString();
        }
    }

    /** Direction and acceleration derived from a left/right and shift click. */
    public record Interaction(boolean increase, boolean accelerated) {
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
