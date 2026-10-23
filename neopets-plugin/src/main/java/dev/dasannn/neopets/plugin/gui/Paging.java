package dev.dasannn.neopets.plugin.gui;

import java.util.List;
import java.util.Objects;

/** Paging calculations for the standard seven-by-four NeoPets content area. */
public final class Paging {
    /** Slots in visual reading order for the 7x4 content area. */
    public static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    /** Number of entries on a full page. */
    public static final int PAGE_SIZE = CONTENT_SLOTS.size();

    private Paging() {
    }

    /** Selects a clamped page and reports navigation state. */
    public static <T> Page<T> page(List<T> values, int requestedPage) {
        Objects.requireNonNull(values, "values");
        if (requestedPage < 0) {
            throw new IllegalArgumentException("requestedPage must not be negative");
        }
        int totalPages = Math.max(1, (values.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int pageIndex = Math.min(requestedPage, totalPages - 1);
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, values.size());
        return new Page<>(values.subList(from, to), pageIndex, totalPages);
    }

    /** One immutable page of values and its navigation metadata. */
    public record Page<T>(List<T> values, int pageIndex, int totalPages) {
        public Page {
            values = List.copyOf(values);
            if (pageIndex < 0 || totalPages < 1 || pageIndex >= totalPages) {
                throw new IllegalArgumentException("invalid page metadata");
            }
        }

        public boolean hasPrevious() {
            return pageIndex > 0;
        }

        public boolean hasNext() {
            return pageIndex + 1 < totalPages;
        }

        public int displayNumber() {
            return pageIndex + 1;
        }
    }
}
