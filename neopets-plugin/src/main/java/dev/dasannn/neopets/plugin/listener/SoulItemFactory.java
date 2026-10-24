package dev.dasannn.neopets.plugin.listener;

import dev.dasannn.neopets.core.model.OwnedPet;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.pet.PendingSoulDelivery;
import dev.dasannn.neopets.plugin.command.LocalizedMessages;
import dev.dasannn.neopets.plugin.config.ConfigurationManager;
import java.util.Objects;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Builds the configured Bukkit item carrying a durable soul-token identity. */
public final class SoulItemFactory {
    private final ConfigurationManager configuration;
    private final LocalizedMessages messages;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SoulItemFactory(ConfigurationManager configuration, LocalizedMessages messages) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Creates one soul item for a pending token and its persisted pet. */
    public ItemStack create(PendingSoulDelivery delivery, OwnedPet pet) {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(pet, "pet");
        if (!delivery.petId().equals(pet.id()) || !delivery.ownerId().equals(pet.owner())) {
            throw new IllegalArgumentException("pending delivery does not match pet identity");
        }
        var snapshot = configuration.current();
        PetVariant variant = snapshot.variants().findById(pet.variant())
                .orElseThrow(() -> new IllegalStateException("Unknown variant: " + pet.variant()));
        String petName = pet.customName().orElse(variant.displayNameKey());
        String typeKey = "gui.pets.type-" + variant.type().value().replace('_', '-');
        String typeName = messages.plain(typeKey);
        var settings = snapshot.settings().features().death().soulItem();
        Material material = Objects.requireNonNull(Material.matchMaterial(settings.material()),
                "configured soul item material");
        TagResolver placeholders = TagResolver.resolver(
                Placeholder.unparsed("pet", petName), Placeholder.unparsed("type", typeName));
        var item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(miniMessage.deserialize(settings.name(), placeholders));
            meta.lore(settings.lore().stream()
                    .map(line -> miniMessage.deserialize(line, placeholders)).toList());
            var data = meta.getPersistentDataContainer();
            data.set(SoulItemKeys.TOKEN_ID, PersistentDataType.STRING, delivery.tokenId().toString());
            data.set(SoulItemKeys.PET_ID, PersistentDataType.STRING, delivery.petId().toString());
            data.set(SoulItemKeys.OWNER, PersistentDataType.STRING, delivery.ownerId().toString());
        });
        return item;
    }

    /** Resolves the same configured pet name used by the soul item. */
    public String petName(OwnedPet pet) {
        Objects.requireNonNull(pet, "pet");
        return pet.customName().orElseGet(() -> configuration.current().variants().findById(pet.variant())
                .map(PetVariant::displayNameKey).orElse(pet.variant().value()));
    }
}
