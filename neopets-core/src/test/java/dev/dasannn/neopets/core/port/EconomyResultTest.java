package dev.dasannn.neopets.core.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EconomyResultTest {
    @Test
    void successRequiresFiniteResultingBalance() {
        assertEquals(12.5, new EconomyResult.Success(12.5).balance());
        assertThrows(IllegalArgumentException.class, () -> new EconomyResult.Success(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new EconomyResult.Success(Double.POSITIVE_INFINITY));
    }

    @Test
    void failureRequiresExplanation() {
        var result = new EconomyResult.Failure("insufficient funds");

        assertInstanceOf(EconomyResult.class, result);
        assertEquals("insufficient funds", result.reason());
        assertThrows(NullPointerException.class, () -> new EconomyResult.Failure(null));
        assertThrows(IllegalArgumentException.class, () -> new EconomyResult.Failure(" "));
    }
}
