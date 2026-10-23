package dev.dasannn.neopets.platform.paper;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.persistence.PersistentDataType;

/** Spawns and initializes NeoPets entities using the matching type-specific appearance applier. */
public final class PetSpawner {
    private final Map<PetTypeId, VariantApplier<? extends LivingEntity>> appliers;

    /** Creates a spawner for the five MVP pet types. */
    public PetSpawner() {
        this(Map.of(
                new PetTypeId("dog"), new WolfVariantApplier(),
                new PetTypeId("cat"), new CatVariantApplier(),
                new PetTypeId("parrot"), new ParrotVariantApplier(),
                new PetTypeId("polar_bear"), new PolarBearVariantApplier(),
                new PetTypeId("donkey"), new DonkeyVariantApplier()));
    }

    /** Creates a spawner with an explicit immutable pet-type registry. */
    public PetSpawner(Map<PetTypeId, ? extends VariantApplier<? extends LivingEntity>> appliers) {
        Objects.requireNonNull(appliers, "appliers");
        this.appliers = Map.copyOf(appliers);
    }

    /**
     * Spawns one pet and applies its appearance, permanent baby state, ownership, and PDC tags.
     *
     * <p>This method must be called from the server's main thread.
     *
     * @throws IllegalArgumentException when the type is unsupported or its appearance is invalid
     */
    public Entity spawn(Location location, UUID ownerUuid, PetId petId, PetVariant variant) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(variant, "variant");

        var applier = appliers.get(variant.type());
        if (applier == null) {
            throw new IllegalArgumentException("Unsupported pet type: " + variant.type());
        }
        var errors = applier.validate(variant.appearance());
        AppearanceRules.requireValid(variant.type().toString(), errors);

        var world = Objects.requireNonNull(location.getWorld(), "location world");
        return spawnWithApplier(world, location, ownerUuid, petId, variant, applier);
    }

    private static <E extends LivingEntity> E spawnWithApplier(
            org.bukkit.World world,
            Location location,
            UUID ownerUuid,
            PetId petId,
            PetVariant variant,
            VariantApplier<E> applier
    ) {
        return world.spawn(location, applier.entityClass(), SpawnReason.CUSTOM, spawned -> {
            applier.apply(spawned, variant.appearance());
            tag(spawned, ownerUuid, petId, variant);

            spawned.setPersistent(false);
            spawned.setRemoveWhenFarAway(false);
            spawned.setInvulnerable(false);
            spawned.setSilent(false);
            spawned.setCustomNameVisible(spawned.customName() != null);

            if (spawned instanceof Animals animals) {
                animals.setLoveModeTicks(0);
            }
            if (spawned instanceof Ageable ageable) {
                if (variant.baby()) {
                    ageable.setBaby();
                } else {
                    // Positive ages are adult but cannot breed; locking keeps that state permanent.
                    ageable.setAge(1);
                }
                ageable.setAgeLock(true);
            }
            if (spawned instanceof Tameable tameable) {
                tameable.setTamed(true);
                tameable.setOwner(spawned.getServer().getOfflinePlayer(ownerUuid));
            }
            if (spawned instanceof Mob mob) {
                PetBrain.suppressConflictingVanillaGoals(mob);
            }
        });
    }

    private static void tag(Entity entity, UUID ownerUuid, PetId petId, PetVariant variant) {
        var data = entity.getPersistentDataContainer();
        data.set(PetKeys.PET_ID, PersistentDataType.STRING, petId.toString());
        data.set(PetKeys.OWNER, PersistentDataType.STRING, ownerUuid.toString());
        data.set(PetKeys.VARIANT_ID, PersistentDataType.STRING, variant.id().toString());
    }
}
