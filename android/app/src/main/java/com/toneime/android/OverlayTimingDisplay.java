package com.toneime.android;

import android.content.Context;
import android.os.Handler;
import android.view.View;
import android.widget.TextView;

/** Polling only updates the UI; measurements are captured on the network thread. */
final class OverlayTimingDisplay {
    private final Handler handler;
    private final TextView status;
    private final TextView details;
    private RequestTiming timing;
    private final Runnable tick = this::refresh;

    OverlayTimingDisplay(Handler handler, TextView status, TextView details) {
        this.handler = handler;
        this.status = status;
        this.details = details;
    }

    void start(RequestTiming value) {
        clear();
        timing = value;
        details.setVisibility(View.VISIBLE);
        refresh();
    }

    void clear() {
        handler.removeCallbacks(tick);
        if (timing != null) timing.finish(RequestTiming.Outcome.CANCELLED);
        timing = null;
        details.setText("");
        details.setVisibility(View.GONE);
    }

    void refresh() {
        handler.removeCallbacks(tick);
        if (timing == null) return;
        Context context = status.getContext();
        RequestTiming.Snapshot snapshot = timing.snapshot();
        String phase = context.getString(label(snapshot.stage));
        switch (snapshot.outcome) {
            case RUNNING -> status.setText(context.getString(R.string.overlay_timing_current,
                    phase, duration(context, snapshot.currentStageNanos)));
            case SUCCESS -> status.setText(R.string.overlay_connection_ready);
            case FAILURE -> status.setText(context.getString(R.string.overlay_timing_failed, phase));
            case CANCELLED -> status.setText(R.string.overlay_timing_cancelled);
        }
        StringBuilder text = new StringBuilder(context.getString(R.string.overlay_timing_total,
                duration(context, snapshot.totalNanos)));
        if (snapshot.connectionAttempts > 1) {
            text.append(" · ").append(context.getString(R.string.overlay_timing_attempts,
                    snapshot.connectionAttempts));
        }
        if (snapshot.reusedConnections > 0) {
            text.append(" · ").append(context.getString(R.string.overlay_timing_reused));
        }
        for (RequestTiming.Stage stage : RequestTiming.Stage.values()) {
            text.append(stage.ordinal() % 3 == 0 ? "\n" : " · ")
                    .append(context.getString(label(stage))).append(' ');
            if (snapshot.visited(stage)) {
                text.append(duration(context, snapshot.durationNanos(stage)));
            } else if (snapshot.reusedConnections > 0 && snapshot.connectionAttempts == 0
                    && (stage == RequestTiming.Stage.DNS || stage == RequestTiming.Stage.CONNECT
                    || stage == RequestTiming.Stage.TLS)) {
                text.append(context.getString(R.string.overlay_timing_skipped));
            } else {
                text.append('—');
            }
        }
        details.setText(text);
        if (snapshot.outcome == RequestTiming.Outcome.RUNNING) handler.postDelayed(tick, 100);
    }

    private static String duration(Context context, long nanos) {
        if (nanos > 0 && nanos < 1_000_000) return context.getString(R.string.overlay_timing_sub_ms);
        if (nanos < 1_000_000_000) {
            return context.getString(R.string.overlay_timing_ms, nanos / 1_000_000);
        }
        return context.getString(R.string.overlay_timing_seconds, nanos / 1_000_000_000d);
    }

    private static int label(RequestTiming.Stage stage) {
        return switch (stage) {
            case QUEUE -> R.string.overlay_timing_queue;
            case PREPARE -> R.string.overlay_timing_prepare;
            case DNS -> R.string.overlay_timing_dns;
            case CONNECT -> R.string.overlay_timing_connect;
            case TLS -> R.string.overlay_timing_tls;
            case UPLOAD -> R.string.overlay_timing_upload;
            case WAIT -> R.string.overlay_timing_wait;
            case RECEIVE -> R.string.overlay_timing_receive;
            case PARSE -> R.string.overlay_timing_parse;
        };
    }
}
