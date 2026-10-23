package dev.dasannn.neopets.plugin.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PagingTest {
    @Test
    void emptyContentStillHasOnePage() {
        var page = Paging.page(List.of(), 0);

        assertEquals(0, page.pageIndex());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void thirtyEntriesFillTwentyEightThenTwoSlots() {
        var values = IntStream.range(0, 30).boxed().toList();
        var first = Paging.page(values, 0);
        var second = Paging.page(values, 1);

        assertEquals(28, first.values().size());
        assertEquals(2, second.values().size());
        assertEquals(List.of(28, 29), second.values());
        assertTrue(first.hasNext());
        assertTrue(second.hasPrevious());
        assertFalse(second.hasNext());
    }

    @Test
    void requestedPageIsClampedAfterAFilterShrinksResults() {
        var page = Paging.page(List.of("one"), 8);

        assertEquals(0, page.pageIndex());
        assertEquals(List.of("one"), page.values());
        assertThrows(IllegalArgumentException.class, () -> Paging.page(List.of(), -1));
    }
}
