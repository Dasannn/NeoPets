package dev.dasannn.neopets.plugin.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SoulItemDeliveryTest {
    private final SoulItemDelivery<String> delivery = new SoulItemDelivery<>();

    @Test
    void keepsItemInInventoryWhenItFits() {
        var recipient = new RecordingRecipient(Map.of());

        var result = delivery.deliver("soul", recipient);

        assertEquals(SoulItemDelivery.Result.INVENTORY, result);
        assertEquals(List.of(), recipient.dropped);
    }

    @Test
    void dropsInventoryLeftoverAtOwnersFeetWhenFull() {
        var leftover = "soul";
        var recipient = new RecordingRecipient(Map.of(0, leftover));

        var result = delivery.deliver("soul", recipient);

        assertEquals(SoulItemDelivery.Result.DROPPED, result);
        assertEquals(List.of(leftover), recipient.dropped);
    }

    private static final class RecordingRecipient implements SoulItemDelivery.Recipient<String> {
        private final Map<Integer, String> leftovers;
        private final List<String> dropped = new ArrayList<>();

        private RecordingRecipient(Map<Integer, String> leftovers) {
            this.leftovers = leftovers;
        }

        @Override
        public Map<Integer, String> add(String item) {
            return leftovers;
        }

        @Override
        public void dropAtFeet(String item) {
            dropped.add(item);
        }
    }
}
