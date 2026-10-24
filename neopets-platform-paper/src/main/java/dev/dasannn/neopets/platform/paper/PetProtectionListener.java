package dev.dasannn.neopets.platform.paper;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/** Enforces ownership protections for PDC-tagged pet entities. */
public final class PetProtectionListener implements Listener {
    private final PlayerPetDamagePolicy damagePolicy;
    private final PetDamagePolicy petDamagePolicy;

    /** Creates a listener with owner self-damage protection and pet death enabled. */
    public PetProtectionListener() {
        this(PetDamagePolicy.defaults());
    }

    /** Creates a listener with an injectable player-versus-pet damage policy. */
    public PetProtectionListener(PlayerPetDamagePolicy damagePolicy) {
        this.damagePolicy = Objects.requireNonNull(damagePolicy, "damagePolicy");
        this.petDamagePolicy = damagePolicy instanceof PetDamagePolicy configured ? configured : null;
    }

    /** Prevents a non-owner from leashing a pet. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (isOtherPlayer(event.getEntity(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Prevents a non-owner from riding a pet. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && isOtherPlayer(event.getMount(), player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents non-owner right-click actions, including feeding, breeding, name tags, inventories, and sit toggles.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (isOtherPlayer(event.getRightClicked(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /** Prevents breeding initiated by a player who does not own either tagged parent. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) {
            return;
        }
        if (isOtherPlayer(event.getMother(), player) || isOtherPlayer(event.getFather(), player)) {
            event.setCancelled(true);
        }
    }

    /** Ensures a pet never acquires its owner as an attack target. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player && isOwner(event.getEntity(), player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    /** Applies the injectable player-versus-pet damage rule, including indirect damage sources. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        var ownerId = PetEntityMetadata.ownerId(event.getEntity());
        var attackerId = attackingPlayerId(event.getDamager(), event.getDamageSource());
        if (ownerId.isPresent() && attackerId.isPresent()
                && damagePolicy.denies(ownerId.orElseThrow(), attackerId.orElseThrow())) {
            event.setCancelled(true);
        }
    }

    /** Stops lethal damage and leaves the pet at one health while pet death is disabled. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (petDamagePolicy == null
                || !(event.getEntity() instanceof org.bukkit.entity.LivingEntity pet)
                || !PetEntityMetadata.isPet(pet)
                || !petDamagePolicy.preventsDeath(pet.getHealth(), event.getFinalDamage())) {
            return;
        }
        event.setCancelled(true);
        pet.setHealth(1.0);
    }

    private static boolean isOtherPlayer(Entity pet, Player player) {
        return PetEntityMetadata.ownerId(pet)
                .map(ownerId -> !ownerId.equals(player.getUniqueId()))
                .orElse(false);
    }

    private static boolean isOwner(Entity pet, Player player) {
        return PetEntityMetadata.ownerId(pet)
                .map(ownerId -> ownerId.equals(player.getUniqueId()))
                .orElse(false);
    }

    static Optional<UUID> attackingPlayerId(Entity damager, DamageSource damageSource) {
        if (damageSource.getCausingEntity() instanceof Player player) {
            return Optional.of(player.getUniqueId());
        }
        if (damager instanceof Player player) {
            return Optional.of(player.getUniqueId());
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return Optional.of(player.getUniqueId());
        }
        return Optional.empty();
    }
}
