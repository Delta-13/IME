package com.toneime.android;

import java.util.function.LongSupplier;

/** In-memory, monotonic timings. Never stores URLs, headers, keys, or message text. */
final class RequestTiming {
    enum Stage { QUEUE, PREPARE, DNS, CONNECT, TLS, UPLOAD, WAIT, RECEIVE, PARSE }
    enum Outcome { RUNNING, SUCCESS, FAILURE, CANCELLED }

    private final LongSupplier clock;
    private final long startedAt;
    private final long[] durations = new long[Stage.values().length];
    private final boolean[] visited = new boolean[Stage.values().length];
    private Stage stage = Stage.QUEUE;
    private Outcome outcome = Outcome.RUNNING;
    private long stageStartedAt;
    private long endedAt;
    private int connectionAttempts;
    private int reusedConnections;

    RequestTiming() { this(System::nanoTime); }

    RequestTiming(LongSupplier clock) {
        this.clock = clock;
        startedAt = stageStartedAt = clock.getAsLong();
        visited[stage.ordinal()] = true;
    }

    synchronized void enter(Stage next) {
        if (outcome != Outcome.RUNNING || stage == next) return;
        long now = clock.getAsLong();
        durations[stage.ordinal()] += now - stageStartedAt;
        stage = next;
        stageStartedAt = now;
        visited[next.ordinal()] = true;
    }

    synchronized void connectionAttempt() {
        if (outcome == Outcome.RUNNING) connectionAttempts++;
    }

    synchronized void connectionAcquired(boolean reused) {
        if (outcome == Outcome.RUNNING && reused) reusedConnections++;
    }

    synchronized void finish(Outcome result) {
        if (outcome != Outcome.RUNNING || result == Outcome.RUNNING) return;
        endedAt = clock.getAsLong();
        durations[stage.ordinal()] += endedAt - stageStartedAt;
        outcome = result;
    }

    synchronized Snapshot snapshot() {
        long now = outcome == Outcome.RUNNING ? clock.getAsLong() : endedAt;
        long[] elapsed = durations.clone();
        if (outcome == Outcome.RUNNING) elapsed[stage.ordinal()] += now - stageStartedAt;
        return new Snapshot(stage, outcome, now - startedAt, now - stageStartedAt,
                elapsed, visited.clone(), connectionAttempts, reusedConnections);
    }

    static final class Snapshot {
        final Stage stage;
        final Outcome outcome;
        final long totalNanos;
        final long currentStageNanos;
        final int connectionAttempts;
        final int reusedConnections;
        private final long[] durations;
        private final boolean[] visited;

        private Snapshot(Stage stage, Outcome outcome, long totalNanos, long currentStageNanos,
                long[] durations, boolean[] visited, int connectionAttempts, int reusedConnections) {
            this.stage = stage;
            this.outcome = outcome;
            this.totalNanos = totalNanos;
            this.currentStageNanos = currentStageNanos;
            this.durations = durations;
            this.visited = visited;
            this.connectionAttempts = connectionAttempts;
            this.reusedConnections = reusedConnections;
        }

        boolean visited(Stage value) { return visited[value.ordinal()]; }
        long durationNanos(Stage value) { return durations[value.ordinal()]; }
    }
}
