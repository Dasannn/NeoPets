package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Live-configurable decisions for player damage and lethal pet damage. */
public final class PetDamagePolicy implements PlayerPetDamagePolicy {
    private final BooleanSupplier deathEnabled;
    private final BooleanSupplier pvpEnabled;

    /** Creates a policy backed by suppliers so configuration reloads take effect immediately. */
    public PetDamagePolicy(BooleanSupplier deathEnabled, BooleanSupplier pvpEnabled) {
        this.deathEnabled = Objects.requireNonNull(deathEnabled, "deathEnabled");
        this.pvpEnabled = Objects.requireNonNull(pvpEnabled, "pvpEnabled");
    }

    /** Owners never damage their own pets; other players may do so only while pet PvP is enabled. */
    @Override
    public boolean denies(UUID ownerId, UUID attackingPlayerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(attackingPlayerId, "attackingPlayerId");
        return ownerId.equals(attackingPlayerId) || !pvpEnabled.getAsBoolean();
    }

    /** Returns whether otherwise lethal damage must be stopped because pet death is disabled. */
    public boolean preventsDeath(double health, double finalDamage) {
        if (!Double.isFinite(health) || health <= 0 || !Double.isFinite(finalDamage) || finalDamage < 0) {
            throw new IllegalArgumentException("health and finalDamage must be finite and valid");
        }
        return !deathEnabled.getAsBoolean() && finalDamage >= health;
    }

    /** Default platform behavior before plugin configuration is bound. */
    public static PetDamagePolicy defaults() {
        return new PetDamagePolicy(() -> true, () -> true);
    }
}
