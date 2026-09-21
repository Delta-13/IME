package com.toneime.android;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public final class RequestTimingTest {
    private final AtomicLong clock = new AtomicLong(1_000_000_000);
    private final RequestTiming timing = new RequestTiming(clock::get);
    private final TimingEventListener events = new TimingEventListener(timing);

    private void advance(long millis) { clock.addAndGet(millis * 1_000_000); }
    private long millis(RequestTiming.Stage stage) { return timing.snapshot().durationNanos(stage) / 1_000_000; }

    @Test public void tlsIsExcludedFromConnectionTimeAndStagesSumToTotal() {
        advance(40);
        timing.enter(RequestTiming.Stage.PREPARE);
        advance(10);
        events.dnsStart(null, "example.test");
        advance(20);
        events.dnsEnd(null, "example.test", Collections.emptyList());
        events.connectStart(null, null, null);
        advance(30);
        events.secureConnectStart(null);
        advance(80);
        events.secureConnectEnd(null, null);
        advance(5);
        events.connectEnd(null, null, null, null);
        events.connectionAcquired(null, null);
        events.requestHeadersStart(null);
        advance(7);
        events.requestBodyEnd(null, 500);
        advance(300);
        events.responseHeadersStart(null);
        advance(60);
        timing.enter(RequestTiming.Stage.PARSE);
        advance(2);
        timing.finish(RequestTiming.Outcome.SUCCESS);

        assertEquals(40, millis(RequestTiming.Stage.QUEUE));
        assertEquals(20, millis(RequestTiming.Stage.DNS));
        assertEquals(35, millis(RequestTiming.Stage.CONNECT));
        assertEquals(80, millis(RequestTiming.Stage.TLS));
        assertEquals(7, millis(RequestTiming.Stage.UPLOAD));
        assertEquals(300, millis(RequestTiming.Stage.WAIT));
        assertEquals(60, millis(RequestTiming.Stage.RECEIVE));
        long sum = 0;
        for (RequestTiming.Stage stage : RequestTiming.Stage.values()) sum += millis(stage);
        assertEquals(sum, timing.snapshot().totalNanos / 1_000_000);
        assertEquals(0, timing.snapshot().reusedConnections);
    }

    @Test public void retriesAccumulateAndCurrentAttemptHasItsOwnClock() {
        events.connectStart(null, null, null);
        advance(100);
        timing.enter(RequestTiming.Stage.PREPARE);
        advance(10);
        events.connectStart(null, null, null);
        advance(25);
        assertEquals(2, timing.snapshot().connectionAttempts);
        assertEquals(125, millis(RequestTiming.Stage.CONNECT));
        assertEquals(25_000_000, timing.snapshot().currentStageNanos);
    }

    @Test public void pooledConnectionDoesNotInventDnsOrTlsMeasurements() {
        timing.enter(RequestTiming.Stage.PREPARE);
        events.connectionAcquired(null, null);
        events.requestHeadersStart(null);
        assertEquals(1, timing.snapshot().reusedConnections);
        assertEquals(0, timing.snapshot().connectionAttempts);
        assertFalse(timing.snapshot().visited(RequestTiming.Stage.DNS));
        assertFalse(timing.snapshot().visited(RequestTiming.Stage.CONNECT));
        assertFalse(timing.snapshot().visited(RequestTiming.Stage.TLS));
    }

    @Test public void cancellationFreezesSnapshotAndRejectsLateNetworkEvents() {
        events.dnsStart(null, "example.test");
        advance(200);
        timing.finish(RequestTiming.Outcome.CANCELLED);
        RequestTiming.Snapshot frozen = timing.snapshot();
        advance(10000);
        events.dnsEnd(null, "example.test", Collections.emptyList());
        events.connectStart(null, null, null);
        timing.finish(RequestTiming.Outcome.SUCCESS);
        assertEquals(RequestTiming.Outcome.CANCELLED, timing.snapshot().outcome);
        assertEquals(RequestTiming.Stage.DNS, timing.snapshot().stage);
        assertEquals(frozen.totalNanos, timing.snapshot().totalNanos);
        assertEquals(200, millis(RequestTiming.Stage.DNS));
        assertEquals(0, timing.snapshot().connectionAttempts);
    }

    @Test public void repeatedSnapshotsDoNotDoubleCountAndFailureKeepsStage() {
        events.requestBodyEnd(null, 10);
        advance(100);
        RequestTiming.Snapshot earlier = timing.snapshot();
        advance(50);
        assertEquals(100_000_000, earlier.durationNanos(RequestTiming.Stage.WAIT));
        assertEquals(150, millis(RequestTiming.Stage.WAIT));
        timing.finish(RequestTiming.Outcome.FAILURE);
        advance(1000);
        assertEquals(150, millis(RequestTiming.Stage.WAIT));
        assertEquals(RequestTiming.Stage.WAIT, timing.snapshot().stage);
        assertEquals(RequestTiming.Outcome.FAILURE, timing.snapshot().outcome);
    }
}
