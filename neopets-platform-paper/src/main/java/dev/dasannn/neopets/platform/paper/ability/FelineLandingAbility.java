package dev.dasannn.neopets.platform.paper.ability;

import java.util.Objects;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Cat fall-protection ability registered as the {@code feline_landing} custom effect. */
public final class FelineLandingAbility implements Ability {
    /** Skill-tree custom effect identifier shipped for cats. */
    public static final String ID = "feline_landing";

    private final FelineLandingPolicy policy;

    /** Creates an ability backed by the owner-indexed fall-damage policy. */
    public FelineLandingAbility(FelineLandingPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void onActivate(LivingEntity pet, Player owner, int level) {
        policy.activate(owner.getUniqueId(), pet.getUniqueId());
    }

    @Override
    public void onDeactivate(LivingEntity pet, Player owner, int level) {
        policy.deactivate(owner.getUniqueId(), pet.getUniqueId());
    }
}
