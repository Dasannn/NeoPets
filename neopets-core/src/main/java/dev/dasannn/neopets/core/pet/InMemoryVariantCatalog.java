package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable variant catalog backed by in-memory collections. */
public final class InMemoryVariantCatalog implements VariantCatalog {
    private final Map<VariantId, PetVariant> variantsById;
    private final List<PetVariant> variants;

    /** Creates a catalog, preserving iteration order and rejecting duplicate identifiers. */
    public InMemoryVariantCatalog(Collection<PetVariant> variants) {
        Objects.requireNonNull(variants, "variants");
        var indexedVariants = new LinkedHashMap<VariantId, PetVariant>();
        for (PetVariant variant : variants) {
            Objects.requireNonNull(variant, "variants must not contain null");
            if (indexedVariants.putIfAbsent(variant.id(), variant) != null) {
                throw new IllegalArgumentException("duplicate variant id: " + variant.id());
            }
        }
        variantsById = Map.copyOf(indexedVariants);
        this.variants = List.copyOf(indexedVariants.values());
    }

    @Override
    public Optional<PetVariant> findById(VariantId variantId) {
        Objects.requireNonNull(variantId, "variantId");
        return Optional.ofNullable(variantsById.get(variantId));
    }

    @Override
    public List<PetVariant> findAll() {
        return variants;
    }

    @Override
    public List<PetVariant> findByType(PetTypeId typeId) {
        Objects.requireNonNull(typeId, "typeId");
        return variants.stream().filter(variant -> variant.type().equals(typeId)).toList();
    }
}
