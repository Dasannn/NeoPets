package dev.dasannn.neopets.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeLifecycleTest {
    @Test
    void startsInDeclarationOrderAndClosesInReverseOrder() throws Exception {
        var events = new ArrayList<String>();
        var lifecycle = new RuntimeLifecycle();

        lifecycle.start(() -> fake("config", events));
        lifecycle.start(() -> fake("storage", events));
        lifecycle.start(() -> fake("domain", events));
        lifecycle.start(() -> fake("platform", events));
        lifecycle.start(() -> fake("integrations", events));
        lifecycle.close();
        lifecycle.close();

        assertEquals(List.of(
                "start config", "start storage", "start domain", "start platform", "start integrations",
                "stop integrations", "stop platform", "stop domain", "stop storage", "stop config"), events);
    }

    @Test
    void failedStartupCanRollbackOnlyCompletedComponents() throws Exception {
        var events = new ArrayList<String>();
        var lifecycle = new RuntimeLifecycle();
        lifecycle.start(() -> fake("storage", events));

        assertThrows(IllegalStateException.class, () -> lifecycle.start(() -> {
            events.add("start domain");
            throw new IllegalStateException("boom");
        }));
        lifecycle.close();

        assertEquals(List.of("start storage", "start domain", "stop storage"), events);
    }

    private static AutoCloseable fake(String name, List<String> events) {
        events.add("start " + name);
        return () -> events.add("stop " + name);
    }
}
