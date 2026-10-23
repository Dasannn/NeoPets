package dev.dasannn.neopets.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadOnlyViewTest {

    @Test
    void playerPetsCopiesAndExposesAnImmutablePetList() {
        List<PetView> source = new ArrayList<>();
        PlayerPetsView view = new PlayerPetsView(3, source);

        source.add(pet());

        assertEquals(List.of(), view.pets());
        assertThrows(UnsupportedOperationException.class, () -> view.pets().add(pet()));
    }

    @Test
    void skillTreeCopiesAndExposesAnImmutableAttributeList() {
        List<SkillTreeView.Attribute> source = new ArrayList<>();
        SkillTreeView view = new SkillTreeView(source);

        source.add(new SkillTreeView.Attribute("health", 2, 1, 5));

        assertEquals(List.of(), view.attributes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> view.attributes().add(new SkillTreeView.Attribute("health", 2, 1, 5))
        );
    }

    @Test
    void viewListsRejectNullElements() {
        assertThrows(NullPointerException.class, () -> new PlayerPetsView(0, java.util.Arrays.asList(pet(), null)));
        assertThrows(
                NullPointerException.class,
                () -> new SkillTreeView(java.util.Arrays.asList(
                        new SkillTreeView.Attribute("health", 0, 0, 5),
                        null
                ))
        );
    }

    @Test
    void petRequiresAllReferenceValues() {
        PetView valid = pet();

        assertThrows(
                NullPointerException.class,
                () -> new PetView(null, valid.owner(), valid.variantId(), valid.typeId(), valid.name(), valid.status(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PetView(valid.id(), null, valid.variantId(), valid.typeId(), valid.name(), valid.status(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PetView(valid.id(), valid.owner(), null, valid.typeId(), valid.name(), valid.status(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PetView(valid.id(), valid.owner(), valid.variantId(), null, valid.name(), valid.status(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PetView(valid.id(), valid.owner(), valid.variantId(), valid.typeId(), null, valid.status(), false)
        );
        assertThrows(
                NullPointerException.class,
                () -> new PetView(valid.id(), valid.owner(), valid.variantId(), valid.typeId(), valid.name(), null, false)
        );
    }

    @Test
    void attributeRequiresAnIdentifier() {
        assertThrows(NullPointerException.class, () -> new SkillTreeView.Attribute(null, 0, 0, 5));
    }

    private static PetView pet() {
        return new PetView(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "dog_brown",
                "dog",
                "Firulais",
                PetStatus.ALIVE,
                false
        );
    }
}
