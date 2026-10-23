package dev.dasannn.neopets.platform.paper.ability;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.platform.paper.PetKeys;
import dev.dasannn.neopets.platform.paper.effect.AbilityRegistry;
import dev.dasannn.neopets.platform.paper.effect.AttributeEffectAdapter;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/** Runtime registry that owns custom ability definitions and their live per-pet activations. */
public final class DefaultAbilityRegistry implements AbilityRegistry {
    private final Map<String, Ability> abilities;
    private final Map<PetId, Map<String, Activation>> active = new HashMap<>();
    private final CooldownTracker cooldowns;
    private final Clock clock;
    private final Function<LivingEntity, Optional<Player>> ownerLookup;
    private final ToDoubleFunction<LivingEntity> detectionRadiusBonusLookup;

    /** Creates a registry that resolves owners from the standard NeoPets entity metadata. */
    public DefaultAbilityRegistry(Collection<? extends Ability> abilities, CooldownTracker cooldowns, Clock clock) {
        this(abilities, cooldowns, clock, DefaultAbilityRegistry::ownerFromMetadata,
                DefaultAbilityRegistry::detectionRadiusBonus);
    }

    /** Creates a registry with an injectable owner lookup for isolated tests. */
    public DefaultAbilityRegistry(
            Collection<? extends Ability> abilities,
            CooldownTracker cooldowns,
            Clock clock,
            Function<LivingEntity, Optional<Player>> ownerLookup
    ) {
        this(abilities, cooldowns, clock, ownerLookup, ignored -> 0.0);
    }

    /** Creates a registry with injectable owner and vanilla-effect lookups for isolated tests. */
    public DefaultAbilityRegistry(
            Collection<? extends Ability> abilities,
            CooldownTracker cooldowns,
            Clock clock,
            Function<LivingEntity, Optional<Player>> ownerLookup,
            ToDoubleFunction<LivingEntity> detectionRadiusBonusLookup
    ) {
        Objects.requireNonNull(abilities, "abilities");
        var indexed = new LinkedHashMap<String, Ability>();
        for (Ability ability : abilities) {
            Objects.requireNonNull(ability, "ability");
            String id = Objects.requireNonNull(ability.id(), "ability.id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("ability id must not be blank");
            }
            if (indexed.putIfAbsent(id, ability) != null) {
                throw new IllegalArgumentException("duplicate ability id: " + id);
            }
        }
        this.abilities = Map.copyOf(indexed);
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ownerLookup = Objects.requireNonNull(ownerLookup, "ownerLookup");
        this.detectionRadiusBonusLookup = Objects.requireNonNull(
                detectionRadiusBonusLookup, "detectionRadiusBonusLookup");
    }

    /** Returns the immutable set of registered custom effect identifiers. */
    public Set<String> registeredIds() {
        return abilities.keySet();
    }

    @Override
    public boolean update(
            String abilityId,
            PetId petId,
            LivingEntity entity,
            int activeLevel,
            Map<String, String> parameters
    ) {
        Objects.requireNonNull(abilityId, "abilityId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(parameters, "parameters");
        Ability ability = abilities.get(abilityId);
        if (ability == null) {
            return false;
        }
        Map<String, Activation> petAbilities = active.computeIfAbsent(petId, ignored -> new HashMap<>());
        Activation previous = petAbilities.get(abilityId);
        if (activeLevel < 1) {
            deactivate(petId, abilityId, petAbilities, previous);
            return true;
        }
        Player owner = Objects.requireNonNull(ownerLookup.apply(entity), "ownerLookup result")
                .orElseThrow(() -> new IllegalStateException("No online owner for live pet " + petId));
        if (previous != null
                && previous.level() == activeLevel
                && previous.pet().getUniqueId().equals(entity.getUniqueId())
                && previous.owner().getUniqueId().equals(owner.getUniqueId())) {
            return true;
        }
        if (previous != null) {
            deactivate(petId, abilityId, petAbilities, previous);
        }
        ability.onActivate(entity, owner, activeLevel);
        active.computeIfAbsent(petId, ignored -> new HashMap<>())
                .put(abilityId, new Activation(ability, entity, owner, activeLevel));
        return true;
    }

    @Override
    public void tick(PetId petId, LivingEntity entity, Player owner) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(owner, "owner");
        Map<String, Activation> petAbilities = active.get(petId);
        if (petAbilities == null) {
            return;
        }
        for (var entry : List.copyOf(petAbilities.entrySet())) {
            Activation activation = entry.getValue();
            if (!activation.pet().getUniqueId().equals(entity.getUniqueId())
                    || !activation.owner().getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            activation.ability().tick(new AbilityContext(
                    petId, entity, owner, activation.level(), clock.instant(), cooldowns,
                    detectionRadiusBonusLookup.applyAsDouble(entity), entry.getKey()));
        }
    }

    @Override
    public void retain(PetId petId, Set<String> activeIds) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(activeIds, "activeIds");
        Map<String, Activation> petAbilities = active.get(petId);
        if (petAbilities != null) {
            for (String id : Set.copyOf(petAbilities.keySet())) {
                if (!activeIds.contains(id)) {
                    deactivate(petId, id, petAbilities, petAbilities.get(id));
                }
            }
        }
    }

    @Override
    public void maintain() {
        cooldowns.evictExpired(clock.instant());
    }

    @Override
    public void deactivate(PetId petId) {
        Objects.requireNonNull(petId, "petId");
        Map<String, Activation> removed = active.remove(petId);
        if (removed != null) {
            removed.values().forEach(DefaultAbilityRegistry::notifyDeactivated);
        }
    }

    @Override
    public void deactivateAll() {
        active.values().forEach(activations -> activations.values()
                .forEach(DefaultAbilityRegistry::notifyDeactivated));
        active.clear();
        cooldowns.clear();
    }

    private void deactivate(
            PetId petId,
            String abilityId,
            Map<String, Activation> petAbilities,
            Activation activation
    ) {
        if (activation != null) {
            petAbilities.remove(abilityId);
            notifyDeactivated(activation);
        }
        if (petAbilities.isEmpty()) {
            active.remove(petId);
        }
    }

    private static void notifyDeactivated(Activation activation) {
        activation.ability().onDeactivate(activation.pet(), activation.owner(), activation.level());
    }

    private static Optional<Player> ownerFromMetadata(LivingEntity entity) {
        String encoded = entity.getPersistentDataContainer().get(PetKeys.OWNER, PersistentDataType.STRING);
        if (encoded == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(entity.getServer().getPlayer(UUID.fromString(encoded)))
                    .filter(Player::isOnline);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private static double detectionRadiusBonus(LivingEntity entity) {
        var attribute = RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE)
                .getOrThrow(NamespacedKey.minecraft("follow_range"));
        return detectionRadiusBonus(entity.getAttribute(attribute));
    }

    static double detectionRadiusBonus(AttributeInstance instance) {
        if (instance == null) {
            return 0.0;
        }
        return java.util.stream.Stream.of("scent", "alert_range")
                .map(id -> instance.getModifier(AttributeEffectAdapter.modifierKey(id)))
                .filter(Objects::nonNull)
                .mapToDouble(modifier -> Math.max(0.0, modifier.getAmount()))
                .sum();
    }

    private record Activation(Ability ability, LivingEntity pet, Player owner, int level) {
    }
}
