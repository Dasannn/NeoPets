package dev.dasannn.neopets.platform.paper.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.dasannn.neopets.core.skilltree.AttributeCategory;
import dev.dasannn.neopets.core.skilltree.AttributeDefinition;
import dev.dasannn.neopets.core.skilltree.VanillaEffectReference;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class AttributeEffectAdapterTest {
    @Test
    void maxHealthModifierIsIdempotentAndPreservesHealthRatio() {
        var attribute = new FakeAttributeInstance(10.0);
        var entity = new FakeLivingEntity(attribute, 5.0);
        Attribute paperAttribute = paperAttribute();
        var adapter = new AttributeEffectAdapter(
                new VanillaEffectCatalog(), ignored -> paperAttribute,
                ignored -> {
                    throw new AssertionError("potion lookup was not expected");
                });
        var health = vanilla("health", "max_health", "20");

        adapter.apply(health, 1, entity.proxy());

        assertEquals(20.0, attribute.value());
        assertEquals(10.0, entity.health());
        assertEquals(1, attribute.modifiers().size());

        adapter.apply(health, 1, entity.proxy());

        assertEquals(20.0, attribute.value());
        assertEquals(10.0, entity.health());
        assertEquals(1, attribute.modifiers().size());

        adapter.apply(health, 0, entity.proxy());

        assertEquals(10.0, attribute.value());
        assertEquals(5.0, entity.health());
        assertEquals(0, attribute.modifiers().size());
    }

    private static AttributeDefinition vanilla(String id, String effectId, String value) {
        return new AttributeDefinition(
                id,
                AttributeCategory.VANILLA,
                1,
                new VanillaEffectReference(effectId, List.of(new BigDecimal(value))),
                List.of(BigDecimal.ZERO));
    }

    private static final class FakeAttributeInstance {
        private final double baseValue;
        private final List<AttributeModifier> modifiers = new ArrayList<>();
        private final AttributeInstance proxy;

        private FakeAttributeInstance(double baseValue) {
            this.baseValue = baseValue;
            proxy = (AttributeInstance) Proxy.newProxyInstance(
                    AttributeEffectAdapterTest.class.getClassLoader(),
                    new Class<?>[] {AttributeInstance.class},
                    (ignored, method, arguments) -> switch (method.getName()) {
                        case "getBaseValue", "getDefaultValue" -> baseValue;
                        case "getValue" -> value();
                        case "getModifiers" -> modifiers();
                        case "addModifier", "addTransientModifier" -> {
                            modifiers.add((AttributeModifier) arguments[0]);
                            yield null;
                        }
                        case "removeModifier" -> {
                            if (arguments[0] instanceof net.kyori.adventure.key.Key key) {
                                modifiers.removeIf(modifier -> modifier.getKey().equals(key));
                            } else {
                                modifiers.remove(arguments[0]);
                            }
                            yield null;
                        }
                        case "toString" -> "fake max-health attribute";
                        default -> throw new AssertionError("Unexpected attribute call: " + method);
                    });
        }

        private double value() {
            return baseValue + modifiers.stream().mapToDouble(AttributeModifier::getAmount).sum();
        }

        private Collection<AttributeModifier> modifiers() {
            return List.copyOf(modifiers);
        }
    }

    private static final class FakeLivingEntity {
        private final FakeAttributeInstance attribute;
        private final LivingEntity proxy;
        private double health;

        private FakeLivingEntity(FakeAttributeInstance attribute, double health) {
            this.attribute = attribute;
            this.health = health;
            proxy = (LivingEntity) Proxy.newProxyInstance(
                    AttributeEffectAdapterTest.class.getClassLoader(),
                    new Class<?>[] {LivingEntity.class},
                    (ignored, method, arguments) -> switch (method.getName()) {
                        case "getAttribute" -> attribute.proxy;
                        case "getHealth" -> this.health;
                        case "setHealth" -> {
                            this.health = (double) arguments[0];
                            yield null;
                        }
                        case "toString" -> "fake living entity";
                        default -> throw new AssertionError("Unexpected entity call: " + method);
                    });
        }

        private LivingEntity proxy() {
            return proxy;
        }

        private double health() {
            return health;
        }
    }

    private static Attribute paperAttribute() {
        return (Attribute) Proxy.newProxyInstance(
                AttributeEffectAdapterTest.class.getClassLoader(),
                new Class<?>[] {Attribute.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "toString" -> "fake max-health attribute type";
                    default -> throw new AssertionError("Unexpected attribute type call: " + method);
                });
    }
}
