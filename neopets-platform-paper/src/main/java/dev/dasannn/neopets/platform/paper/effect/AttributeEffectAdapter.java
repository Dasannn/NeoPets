package dev.dasannn.neopets.platform.paper.effect;

import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Function;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;

/** Applies one vanilla skill-tree effect to a Paper living entity. */
public final class AttributeEffectAdapter {
    private static final String MODIFIER_NAMESPACE = "neopets";
    private static final String MODIFIER_PREFIX = "skill/";

    private final VanillaEffectCatalog catalog;
    private final Function<NamespacedKey, Attribute> attributeLookup;
    private final Function<NamespacedKey, org.bukkit.potion.PotionEffectType> potionEffectLookup;

    /** Creates an adapter backed by the supplied catalog. */
    public AttributeEffectAdapter(VanillaEffectCatalog catalog) {
        this(catalog,
                key -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE).getOrThrow(key),
                key -> RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.MOB_EFFECT).getOrThrow(key));
    }

    AttributeEffectAdapter(
            VanillaEffectCatalog catalog,
            Function<NamespacedKey, Attribute> attributeLookup,
            Function<NamespacedKey, org.bukkit.potion.PotionEffectType> potionEffectLookup
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.attributeLookup = Objects.requireNonNull(attributeLookup, "attributeLookup");
        this.potionEffectLookup = Objects.requireNonNull(potionEffectLookup, "potionEffectLookup");
    }

    /**
     * Removes the previous application and applies the selected active level.
     * An active level of zero only removes the effect.
     */
    public void apply(AttributeDefinition definition, int activeLevel, LivingEntity entity) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(entity, "entity");
        if (!(definition.effect() instanceof VanillaEffectReference reference)) {
            throw new IllegalArgumentException("attribute does not reference a vanilla effect: " + definition.id());
        }

        var effect = catalog.find(reference.catalogEffectId()).orElseThrow(
                () -> new IllegalArgumentException("unknown vanilla effect: " + reference.catalogEffectId()));
        switch (effect) {
            case VanillaEffectCatalog.AttributeEffect attributeEffect ->
                    applyAttribute(definition.id(), reference, activeLevel, attributeEffect, entity);
            case VanillaEffectCatalog.PotionEffect potionEffect ->
                    applyPotion(reference, activeLevel, potionEffect, entity);
        }
    }

    /** Returns the stable modifier key used for one configured attribute branch. */
    public static NamespacedKey modifierKey(String attributeId) {
        Objects.requireNonNull(attributeId, "attributeId");
        return new NamespacedKey(MODIFIER_NAMESPACE, MODIFIER_PREFIX + attributeId);
    }

    private void applyAttribute(
            String attributeId,
            VanillaEffectReference reference,
            int activeLevel,
            VanillaEffectCatalog.AttributeEffect effect,
            LivingEntity entity
    ) {
        Attribute attribute = Objects.requireNonNull(
                attributeLookup.apply(effect.attributeKey()), "attributeLookup result");
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        boolean health = effect.maximumHealth();
        double previousMaximum = health ? instance.getValue() : 0.0;
        double previousHealthRatio = health && previousMaximum > 0.0
                ? Math.clamp(entity.getHealth() / previousMaximum, 0.0, 1.0)
                : 0.0;

        NamespacedKey key = modifierKey(attributeId);
        instance.removeModifier(key);
        catalog.valueAtLevel(reference, activeLevel).ifPresent(configured ->
                addModifier(instance, key, configured, effect));

        if (health) {
            double changedMaximum = instance.getValue();
            entity.setHealth(Math.min(changedMaximum, previousHealthRatio * changedMaximum));
        }
    }

    private static void addModifier(
            AttributeInstance instance,
            NamespacedKey key,
            BigDecimal configured,
            VanillaEffectCatalog.AttributeEffect effect
    ) {
        double rawValue = configured.doubleValue();
        if (!Double.isFinite(rawValue)) {
            throw new IllegalArgumentException("configured effect value must be finite");
        }
        double targetValue = effect.transform().apply(rawValue);
        double amount = targetValue - instance.getBaseValue();
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void applyPotion(
            VanillaEffectReference reference,
            int activeLevel,
            VanillaEffectCatalog.PotionEffect effect,
            LivingEntity entity
    ) {
        var type = Objects.requireNonNull(
                potionEffectLookup.apply(effect.typeKey()), "potionEffectLookup result");
        entity.removePotionEffect(type);
        if (catalog.valueAtLevel(reference, activeLevel).isEmpty()) {
            return;
        }
        int amplifier = effect.amplifierByLevel() ? activeLevel - 1 : 0;
        entity.addPotionEffect(new PotionEffect(
                type, PotionEffect.INFINITE_DURATION, amplifier, true, false, false));
    }
}
