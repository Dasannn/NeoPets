package dev.dasannn.neopets.core.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class KeyedSequencerTest {
    @Test
    void runsEqualKeysStrictlyInSubmissionOrderAndRemovesIdleEntries() {
        var sequencer = new KeyedSequencer<String>();
        var starts = new ArrayList<Integer>();
        var firstCompletion = new CompletableFuture<Integer>();

        var first = sequencer.submit("player", () -> {
            starts.add(1);
            return firstCompletion;
        });
        var second = sequencer.submit("player", () -> {
            starts.add(2);
            return CompletableFuture.completedFuture(20);
        });

        assertEquals(List.of(1), starts);
        assertFalse(second.isDone());
        assertEquals(1, sequencer.trackedKeyCount());

        firstCompletion.complete(10);

        assertEquals(10, first.join());
        assertEquals(20, second.join());
        assertEquals(List.of(1, 2), starts);
        assertEquals(0, sequencer.trackedKeyCount());
    }

    @Test
    void startsDifferentKeysIndependently() {
        var sequencer = new KeyedSequencer<String>();
        var firstCompletion = new CompletableFuture<Integer>();
        var secondCompletion = new CompletableFuture<Integer>();

        var first = sequencer.submit("first", () -> firstCompletion);
        var second = sequencer.submit("second", () -> secondCompletion);

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertEquals(2, sequencer.trackedKeyCount());

        secondCompletion.complete(2);
        firstCompletion.complete(1);
        assertEquals(1, first.join());
        assertEquals(2, second.join());
    }

    @Test
    void failuresAndThrownActionsDoNotBlockTheQueue() {
        var sequencer = new KeyedSequencer<String>();
        var failed = new CompletableFuture<Integer>();
        var first = sequencer.submit("player", () -> failed);
        var second = sequencer.submit("player", () -> {
            throw new IllegalStateException("action failed before returning a future");
        });
        var third = sequencer.submit("player", () -> CompletableFuture.completedFuture(3));

        failed.completeExceptionally(new IllegalArgumentException("asynchronous failure"));

        assertThrows(CompletionException.class, first::join);
        assertThrows(CompletionException.class, second::join);
        assertEquals(3, third.join());
        assertEquals(0, sequencer.trackedKeyCount());
    }

    @Test
    void cancellingTheReturnedFutureDoesNotReleaseAStillRunningAction() {
        var sequencer = new KeyedSequencer<String>();
        var actionCompletion = new CompletableFuture<Integer>();
        var starts = new ArrayList<Integer>();
        var first = sequencer.submit("player", () -> actionCompletion);
        var second = sequencer.submit("player", () -> {
            starts.add(2);
            return CompletableFuture.completedFuture(2);
        });

        first.cancel(false);

        assertEquals(List.of(), starts);
        assertFalse(second.isDone());
        actionCompletion.complete(1);
        assertEquals(2, second.join());
        assertEquals(List.of(2), starts);
    }

    @Test
    void validatesArgumentsAndNullActionResults() {
        var sequencer = new KeyedSequencer<String>();

        assertThrows(NullPointerException.class,
                () -> sequencer.submit(null, () -> CompletableFuture.completedFuture(1)));
        assertThrows(NullPointerException.class, () -> sequencer.submit("player", null));
        var nullResult = sequencer.<Integer>submit("player", () -> null);
        assertThrows(CompletionException.class, nullResult::join);
        assertTrue(nullResult.isCompletedExceptionally());
        assertEquals(0, sequencer.trackedKeyCount());
    }
}
