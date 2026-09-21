package com.toneime.android;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;

/** Each instance belongs to one call, including all its connection attempts. */
final class TimingEventListener extends EventListener {
    private final RequestTiming timing;
    private boolean newConnection;

    TimingEventListener(RequestTiming timing) { this.timing = timing; }

    @Override public void dnsStart(Call call, String domainName) {
        timing.enter(RequestTiming.Stage.DNS);
    }

    @Override public void dnsEnd(Call call, String domainName, List<InetAddress> addresses) {
        timing.enter(RequestTiming.Stage.PREPARE);
    }

    @Override public void connectStart(Call call, InetSocketAddress address, Proxy proxy) {
        timing.connectionAttempt();
        timing.enter(RequestTiming.Stage.CONNECT);
    }

    @Override public void secureConnectStart(Call call) {
        // TLS is nested inside connect; switching phases avoids counting it twice.
        timing.enter(RequestTiming.Stage.TLS);
    }

    @Override public void secureConnectEnd(Call call, Handshake handshake) {
        timing.enter(RequestTiming.Stage.CONNECT);
    }

    @Override public void connectEnd(Call call, InetSocketAddress address, Proxy proxy,
            Protocol protocol) {
        newConnection = true;
        timing.enter(RequestTiming.Stage.PREPARE);
    }

    @Override public void connectionAcquired(Call call, Connection connection) {
        timing.connectionAcquired(!newConnection);
        newConnection = false;
        timing.enter(RequestTiming.Stage.PREPARE);
    }

    @Override public void requestHeadersStart(Call call) {
        // Includes headers and body; completion is a local write, not a server ACK.
        timing.enter(RequestTiming.Stage.UPLOAD);
    }

    @Override public void requestBodyEnd(Call call, long byteCount) {
        timing.enter(RequestTiming.Stage.WAIT);
    }

    @Override public void responseHeadersStart(Call call) {
        // OkHttp 4.3+ fires this after headers arrive, not before the blocking read.
        // This includes network latency, not just the server's processing time.
        timing.enter(RequestTiming.Stage.RECEIVE);
    }
}
