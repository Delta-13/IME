package com.toneime.android;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;

final class OpenAiClient {
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient SHARED_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build();
    private final OkHttpClient httpClient;
    private volatile Call activeCall;
    private volatile boolean cancelled;

    OpenAiClient() { this(SHARED_CLIENT); }

    OpenAiClient(OkHttpClient httpClient) { this.httpClient = httpClient; }

    void cancel() {
        cancelled = true;
        Call call = activeCall;
        if (call != null) call.cancel();
    }

    TranslationProtocol.Result translate(String provider, String baseUrl, String model,
            String apiKey, TranslationProtocol.Request request) throws Exception {
        return translate(provider, baseUrl, model, apiKey, request, new RequestTiming());
    }

    TranslationProtocol.Result translate(String provider, String baseUrl, String model,
            String apiKey, TranslationProtocol.Request request, RequestTiming timing) throws Exception {
        timing.enter(RequestTiming.Stage.PREPARE);
        try {
            ensureActive();
            String normalizedProvider = ApiProvider.normalize(provider);
            String path = ApiProvider.usesClaudeMessages(normalizedProvider)
                    ? "/messages" : "/chat/completions";
            String endpoint = baseUrl.trim().replaceAll("/+$", "") + path;
            Request.Builder builder = new Request.Builder().url(endpoint);
            if (!builder.build().url().isHttps()) {
                throw new IllegalArgumentException("Android 端只允许 HTTPS API 地址，以免泄露 API Key。");
            }
            byte[] requestBytes = (ApiProvider.usesClaudeMessages(normalizedProvider)
                    ? TranslationProtocol.buildClaudePayload(model, request)
                    : TranslationProtocol.buildPayload(normalizedProvider, model, request))
                    .toString().getBytes(StandardCharsets.UTF_8);
            builder.header("Accept", "application/json").post(new RequestBody() {
                @Override public MediaType contentType() { return JSON; }
                @Override public long contentLength() { return requestBytes.length; }
                @Override public void writeTo(BufferedSink sink) throws IOException {
                    sink.write(requestBytes);
                    // Include the final local flush in upload timing, before requestBodyEnd.
                    sink.flush();
                }
            });
            if (ApiProvider.usesClaudeMessages(normalizedProvider)) {
                if (apiKey.trim().isEmpty()) throw new IllegalArgumentException("Claude API Key is required.");
                builder.header("x-api-key", apiKey.trim()).header("anthropic-version", "2023-06-01");
            } else if (!apiKey.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }

            // newBuilder shares the connection pool. Only the per-call listener differs.
            Call call = httpClient.newBuilder().eventListener(new TimingEventListener(timing))
                    .followRedirects(false).followSslRedirects(false)
                    .build().newCall(builder.build());
            activeCall = call;
            ensureActive();
            try (Response response = call.execute()) {
                String body = response.body() == null ? "" : readLimited(response.body().byteStream());
                ensureActive();
                if (!response.isSuccessful()) {
                    throw new IOException("模型请求失败（" + response.code() + "）：" + providerError(body));
                }
                timing.enter(RequestTiming.Stage.PARSE);
                TranslationProtocol.Result result = ApiProvider.usesClaudeMessages(normalizedProvider)
                        ? TranslationProtocol.parseClaudeApiResponse(body)
                        : TranslationProtocol.parseApiResponse(body);
                timing.finish(RequestTiming.Outcome.SUCCESS);
                return result;
            }
        } catch (Exception exception) {
            timing.finish(cancelled || Thread.currentThread().isInterrupted()
                    ? RequestTiming.Outcome.CANCELLED : RequestTiming.Outcome.FAILURE);
            if (exception instanceof SocketTimeoutException) {
                throw new IOException("模型请求超时，请重试。", exception);
            }
            throw exception;
        } finally {
            activeCall = null;
        }
    }

    private void ensureActive() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
            Call call = activeCall;
            if (call != null) call.cancel();
            throw new IOException("Translation request was cancelled.");
        }
    }

    private static String readLimited(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("模型响应超过 1 MB。");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String providerError(String body) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (message.trim().isEmpty()) return "未提供错误信息";
            String compact = message.replaceAll("\\s+", " ").trim();
            return compact.length() <= 180 ? compact : compact.substring(0, 180) + "…";
        } catch (Exception ignored) {
            return "未提供可解析的错误信息";
        }
    }
}
