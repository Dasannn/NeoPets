package dev.dasannn.neopets.core.pet;

import dev.dasannn.neopets.core.model.PetTypeId;
import dev.dasannn.neopets.core.model.PetVariant;
import dev.dasannn.neopets.core.model.VariantId;
import java.util.List;
import java.util.Optional;

/** Read-only catalog of configured pet variants. */
public interface VariantCatalog {
    /** Looks up a variant by its stable identifier. */
    Optional<PetVariant> findById(VariantId variantId);

    /** Returns every configured variant in catalog order. */
    List<PetVariant> findAll();

    /** Returns variants of the requested type in catalog order. */
    List<PetVariant> findByType(PetTypeId typeId);
}
