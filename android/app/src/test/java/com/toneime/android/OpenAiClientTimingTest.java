package com.toneime.android;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.*;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public final class OpenAiClientTimingTest {
    private MockWebServer server;
    private OkHttpClient http;
    private final TranslationProtocol.Request input = new TranslationProtocol.Request(
            "zh", "ja", "zh", "你好", "friend", "chat", 3, 3, 3);

    @Before public void setUp() throws Exception {
        HeldCertificate certificate = new HeldCertificate.Builder()
                .addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build();
        HandshakeCertificates serverTls = new HandshakeCertificates.Builder()
                .heldCertificate(certificate).build();
        HandshakeCertificates clientTls = new HandshakeCertificates.Builder()
                .addTrustedCertificate(certificate.certificate()).build();
        server = new MockWebServer();
        server.useHttps(serverTls.sslSocketFactory(), false);
        server.setProtocols(Collections.singletonList(Protocol.HTTP_1_1));
        server.start();
        http = new OkHttpClient.Builder()
                .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager())
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .readTimeout(3, TimeUnit.SECONDS).build();
    }

    @After public void tearDown() throws Exception {
        http.connectionPool().evictAll();
        http.dispatcher().executorService().shutdownNow();
        server.shutdown();
    }

    private String responseJson() throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("message", new JSONObject().put("content", "{\"primary\":\"こんにちは\"}")))).toString();
    }

    private TranslationProtocol.Result translate(OpenAiClient client, RequestTiming timing) throws Exception {
        return client.translate(ApiProvider.CUSTOM, server.url("/v1").toString(),
                "test-model", "test-key", input, timing);
    }

    @Test public void realHttpsDelaysAreAttributedToWaitAndReceiveAndConnectionIsReused() throws Exception {
        server.enqueue(new MockResponse().setBody(responseJson())
                .setHeadersDelay(180, TimeUnit.MILLISECONDS).setBodyDelay(140, TimeUnit.MILLISECONDS));
        RequestTiming first = new RequestTiming();
        assertEquals("こんにちは", translate(new OpenAiClient(http), first).candidates.get(0).text);
        RequestTiming.Snapshot measured = first.snapshot();
        assertEquals(RequestTiming.Outcome.SUCCESS, measured.outcome);
        assertTrue(measured.visited(RequestTiming.Stage.DNS));
        assertTrue(measured.visited(RequestTiming.Stage.TLS));
        assertTrue(measured.durationNanos(RequestTiming.Stage.WAIT) >= TimeUnit.MILLISECONDS.toNanos(150));
        assertTrue(measured.durationNanos(RequestTiming.Stage.RECEIVE) >= TimeUnit.MILLISECONDS.toNanos(110));
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/v1/chat/completions", request.getPath());
        assertEquals("Bearer test-key", request.getHeader("Authorization"));

        server.enqueue(new MockResponse().setBody(responseJson()));
        RequestTiming second = new RequestTiming();
        translate(new OpenAiClient(http), second);
        assertEquals(1, second.snapshot().reusedConnections);
        assertFalse(second.snapshot().visited(RequestTiming.Stage.DNS));
        assertFalse(second.snapshot().visited(RequestTiming.Stage.CONNECT));
        assertFalse(second.snapshot().visited(RequestTiming.Stage.TLS));
    }

    @Test public void cancellationTerminatesARealWaitingRequest() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        OpenAiClient client = new OpenAiClient(http);
        RequestTiming timing = new RequestTiming();
        try {
            Future<?> future = worker.submit(() -> translate(client, timing));
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS));
            client.cancel();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> future.get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals(RequestTiming.Outcome.CANCELLED, timing.snapshot().outcome);
        } finally {
            worker.shutdownNow();
        }
    }

    @Test public void invalidJsonRetainsParseFailureAndCleartextIsRejected() throws Exception {
        server.enqueue(new MockResponse().setBody("not json"));
        RequestTiming timing = new RequestTiming();
        assertThrows(Exception.class, () -> translate(new OpenAiClient(http), timing));
        assertEquals(RequestTiming.Outcome.FAILURE, timing.snapshot().outcome);
        assertEquals(RequestTiming.Stage.PARSE, timing.snapshot().stage);
        assertThrows(IllegalArgumentException.class, () -> new OpenAiClient(http).translate(
                ApiProvider.CUSTOM, "http://localhost", "test", "test-key", input));
    }

    @Test public void productionClientRejectsUntrustedCertificateDuringTls() {
        RequestTiming timing = new RequestTiming();
        assertThrows(IOException.class, () -> translate(new OpenAiClient(), timing));
        assertEquals(RequestTiming.Outcome.FAILURE, timing.snapshot().outcome);
        assertEquals(RequestTiming.Stage.TLS, timing.snapshot().stage);
        assertEquals(0, server.getRequestCount());
    }

    @Test public void claudeHeadersAndPayloadArePreservedAndRedirectsAreNotFollowed() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"primary\\\":\\\"hello\\\"}\"}]}"));
        new OpenAiClient(http).translate(ApiProvider.CLAUDE, server.url("/v1").toString(),
                "test-model", "test-key", input, new RequestTiming());
        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("/v1/messages", request.getPath());
        assertEquals("test-key", request.getHeader("x-api-key"));
        assertNull(request.getHeader("Authorization"));
        server.enqueue(new MockResponse().setResponseCode(302).addHeader("Location", server.url("/other")));
        assertThrows(IOException.class, () -> translate(new OpenAiClient(http), new RequestTiming()));
        assertEquals(2, server.getRequestCount());
    }
}
