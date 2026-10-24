package dev.dasannn.neopets.platform.paper.ability;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Cancels owner fall damage through the constant-time feline landing policy. */
public final class FelineLandingListener implements Listener {
    private final FelineLandingPolicy policy;

    /** Creates the thin Bukkit event adapter. */
    public FelineLandingListener(FelineLandingPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Cancels only vanilla fall damage for currently protected owners. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && event.getEntity() instanceof Player player
                && policy.preventsFallDamage(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Removes dead cat entities before any subsequent owner damage event can consult stale state. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        policy.deactivatePet(event.getEntity().getUniqueId());
    }
}
