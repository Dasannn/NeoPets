package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.dasannn.neopets.core.model.PetId;
import dev.dasannn.neopets.core.model.PlayerId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulItemIdentityTest {
    @Test
    void emptyHandIsReportedAsMissingSoulItem() {
        assertInstanceOf(SoulItemIdentity.ReadResult.Missing.class, SoulItemIdentity.read(null));
    }

    @Test
    void markerWithWrongPdcTypeRemainsProtectedAndFailsValidationWithoutThrowing() {
        var malformed = SoulItemTestItems.item(Map.of(SoulItemKeys.TOKEN_ID, 42));
        assertTrue(SoulItemIdentity.isSoul(malformed));
        assertInstanceOf(SoulItemIdentity.ReadResult.Invalid.class, SoulItemIdentity.read(malformed));
    }

    @Test
    void readsIdentityFromActualPdcAndIgnoresAnUntaggedItem() {
        UUID token = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var soul = SoulItemTestItems.item(Map.of(SoulItemKeys.TOKEN_ID, token.toString(),
                SoulItemKeys.PET_ID, pet.toString(), SoulItemKeys.OWNER, owner.toString()));
        var found = assertInstanceOf(SoulItemIdentity.ReadResult.Found.class, SoulItemIdentity.read(soul));
        assertEquals(new SoulItemIdentity(token, new PetId(pet), new PlayerId(owner)), found.identity());
        var ordinary = SoulItemTestItems.item(Map.of());
        assertFalse(SoulItemIdentity.isSoul(ordinary));
        assertInstanceOf(SoulItemIdentity.ReadResult.Missing.class, SoulItemIdentity.read(ordinary));
    }

    @Test
    void parsesAllThreePdcValuesAndRejectsPartialOrMalformedIdentity() {
        UUID token = new UUID(1, 1);
        UUID pet = new UUID(2, 2);
        UUID owner = new UUID(3, 3);

        var found = assertInstanceOf(SoulItemIdentity.ReadResult.Found.class,
                SoulItemIdentity.parse(token.toString(), pet.toString(), owner.toString()));

        assertEquals(new SoulItemIdentity(token, new PetId(pet), new PlayerId(owner)), found.identity());
        assertInstanceOf(SoulItemIdentity.ReadResult.Invalid.class,
                SoulItemIdentity.parse(token.toString(), null, owner.toString()));
        assertInstanceOf(SoulItemIdentity.ReadResult.Invalid.class,
                SoulItemIdentity.parse("not-a-uuid", pet.toString(), owner.toString()));
    }
}
