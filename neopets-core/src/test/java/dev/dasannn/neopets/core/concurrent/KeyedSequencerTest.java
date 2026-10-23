package dev.dasannn.neopets.core.concurrent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class KeyedSequencerTest {
    @Test
    void awaitAllWaitsForCurrentSequencesButNotLaterSubmissions() {
        var sequencer = new KeyedSequencer<String>();
        var firstGate = new CompletableFuture<Void>();
        var secondGate = new CompletableFuture<Void>();

        var first = sequencer.submit("first", () -> firstGate);
        var barrier = sequencer.awaitAll();
        var later = sequencer.submit("second", () -> secondGate);

        assertFalse(barrier.isDone());
        secondGate.complete(null);
        assertTrue(later.isDone());
        assertFalse(barrier.isDone());

        firstGate.complete(null);
        assertTrue(first.isDone());
        assertTrue(barrier.isDone());
    }

    @Test
    void awaitAllReportsFailuresWithoutStoppingLaterActions() {
        var sequencer = new KeyedSequencer<String>();
        var gate = new CompletableFuture<Void>();
        var failed = sequencer.submit("player", () -> gate);
        var later = sequencer.submit("player", () -> CompletableFuture.completedFuture(null));
        var barrier = sequencer.awaitAll();

        gate.completeExceptionally(new IllegalStateException("write failed"));

        assertFutureFailsWith(IllegalStateException.class, failed);
        assertTrue(later.isDone());
        assertFutureFailsWith(IllegalStateException.class, barrier);
    }

    @Test
    void awaitAllWaitsForTheActionWhenItsPublicResultWasCancelled() {
        var sequencer = new KeyedSequencer<String>();
        var action = new CompletableFuture<Void>();
        var result = sequencer.submit("player", () -> action);
        result.cancel(false);

        var barrier = sequencer.awaitAll();
        assertFalse(barrier.isDone());

        action.complete(null);
        assertTrue(barrier.isDone());
    }

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

        assertFutureFailsWith(IllegalArgumentException.class, first);
        assertFutureFailsWith(IllegalStateException.class, second);
        assertEquals(3, third.join());
        assertEquals(0, sequencer.trackedKeyCount());
    }

    @Test
    void drainsLongQueuesOfImmediatelyCompletedActionsWithoutExhaustingTheStack() {
        var sequencer = new KeyedSequencer<String>();
        var firstCompletion = new CompletableFuture<Integer>();
        var futures = new ArrayList<CompletableFuture<Integer>>();
        futures.add(sequencer.submit("player", () -> firstCompletion));
        for (int index = 1; index <= 10_000; index++) {
            int value = index;
            futures.add(sequencer.submit("player", () -> CompletableFuture.completedFuture(value)));
        }

        firstCompletion.complete(0);

        assertTrue(futures.stream().allMatch(CompletableFuture::isDone));
        for (int index = 0; index < futures.size(); index++) {
            assertEquals(index, futures.get(index).join());
        }
        assertEquals(0, sequencer.trackedKeyCount());
        assertEquals(10_001, sequencer.submit("player", () -> CompletableFuture.completedFuture(10_001)).join());
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
        assertFutureFailsWith(NullPointerException.class, nullResult);
        assertTrue(nullResult.isCompletedExceptionally());
        assertEquals(0, sequencer.trackedKeyCount());
    }

    private static <T extends Throwable> T assertFutureFailsWith(
            Class<T> expectedType, CompletableFuture<?> future) {
        var exception = assertThrows(CompletionException.class, future::join);
        return assertInstanceOf(expectedType, exception.getCause());
    }
}
