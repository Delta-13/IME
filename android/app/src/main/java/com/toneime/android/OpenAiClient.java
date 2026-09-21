package com.toneime.android;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

final class OpenAiClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private volatile HttpsURLConnection activeConnection;
    private volatile boolean cancelled;

    interface ProgressListener {
        void onSending();
        void onWaitingForResponse();
    }

    void cancel() {
        cancelled = true;
        HttpsURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    TranslationProtocol.Result translate(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            TranslationProtocol.Request request) throws Exception {
        return translate(provider, baseUrl, model, apiKey, request, new ProgressListener() {
            @Override
            public void onSending() {
            }

            @Override
            public void onWaitingForResponse() {
            }
        });
    }

    TranslationProtocol.Result translate(
            String provider,
            String baseUrl,
            String model,
            String apiKey,
            TranslationProtocol.Request request,
            ProgressListener progress) throws Exception {
        String normalizedProvider = ApiProvider.normalize(provider);
        String path = ApiProvider.usesClaudeMessages(normalizedProvider)
                ? "/messages"
                : "/chat/completions";
        URL endpoint = new URL(baseUrl.trim().replaceAll("/+$", "") + path);
        if (!"https".equalsIgnoreCase(endpoint.getProtocol())) {
            throw new IllegalArgumentException("Android 端只允许 HTTPS API 地址，以免泄露 API Key。");
        }

        byte[] requestBytes = (ApiProvider.usesClaudeMessages(normalizedProvider)
                ? TranslationProtocol.buildClaudePayload(model, request)
                : TranslationProtocol.buildPayload(normalizedProvider, model, request))
                .toString()
                .getBytes(StandardCharsets.UTF_8);
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        activeConnection = connection;
        try {
            ensureActive();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(requestBytes.length);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (ApiProvider.usesClaudeMessages(normalizedProvider)) {
                if (apiKey.trim().isEmpty()) {
                    throw new IllegalArgumentException("Claude API Key is required.");
                }
                connection.setRequestProperty("x-api-key", apiKey.trim());
                connection.setRequestProperty("anthropic-version", "2023-06-01");
            } else if (!apiKey.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }

            progress.onSending();
            try (OutputStream output = connection.getOutputStream()) {
                ensureActive();
                output.write(requestBytes);
            }
            ensureActive();
            progress.onWaitingForResponse();

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = stream == null ? "" : readLimited(stream);
            ensureActive();
            if (status < 200 || status >= 300) {
                throw new IOException("模型请求失败（" + status + "）：" + providerError(body));
            }

            return ApiProvider.usesClaudeMessages(normalizedProvider)
                    ? TranslationProtocol.parseClaudeApiResponse(body)
                    : TranslationProtocol.parseApiResponse(body);
        } catch (SocketTimeoutException exception) {
            throw new IOException("模型请求超时，请重试。", exception);
        } finally {
            connection.disconnect();
            if (activeConnection == connection) {
                activeConnection = null;
            }
        }
    }

    private void ensureActive() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) {
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
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("模型响应超过 1 MB。");
                }
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String providerError(String body) {
        try {
            JSONObject error = new JSONObject(body).optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "");
            if (message.trim().isEmpty()) {
                return "未提供错误信息";
            }
            String compact = message.replaceAll("\\s+", " ").trim();
            return compact.length() <= 180 ? compact : compact.substring(0, 180) + "…";
        } catch (Exception ignored) {
            return "未提供可解析的错误信息";
        }
    }
}
