package com.toneime.android;

final class ApiProvider {
    static final String OPENAI = "openai";
    static final String CLAUDE = "claude";
    static final String QWEN = "qwen";
    static final String KIMI = "kimi";
    static final String MINIMAX = "minimax";
    static final String DEEPSEEK = "deepseek";
    static final String GOOGLE = "google";
    static final String CUSTOM = "custom";

    private ApiProvider() {
    }

    static String normalize(String value) {
        return switch (value == null ? "" : value) {
            case CLAUDE -> CLAUDE;
            case QWEN -> QWEN;
            case KIMI -> KIMI;
            case MINIMAX -> MINIMAX;
            case DEEPSEEK -> DEEPSEEK;
            case GOOGLE -> GOOGLE;
            case CUSTOM -> CUSTOM;
            default -> OPENAI;
        };
    }

    static boolean usesClaudeMessages(String provider) {
        return CLAUDE.equals(normalize(provider));
    }

    static boolean supportsNoStore(String provider) {
        return OPENAI.equals(normalize(provider));
    }

    static boolean supportsJsonResponseFormat(String provider) {
        String normalized = normalize(provider);
        return OPENAI.equals(normalized) || DEEPSEEK.equals(normalized);
    }

    static String defaultBaseUrl(String provider) {
        return switch (normalize(provider)) {
            case CLAUDE -> "https://api.anthropic.com/v1";
            case QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case KIMI -> "https://api.moonshot.ai/v1";
            case MINIMAX -> "https://api.minimax.io/v1";
            case DEEPSEEK -> "https://api.deepseek.com";
            case GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/openai";
            case CUSTOM, OPENAI -> "https://api.openai.com/v1";
            default -> "https://api.openai.com/v1";
        };
    }

    static String defaultModel(String provider) {
        return switch (normalize(provider)) {
            case CLAUDE -> "claude-sonnet-4-5";
            case QWEN -> "qwen-plus";
            case KIMI -> "kimi-k3";
            case MINIMAX -> "MiniMax-M2.7";
            case DEEPSEEK -> "deepseek-v4-flash";
            case GOOGLE -> "gemini-3.6-flash";
            case CUSTOM, OPENAI -> "gpt-5.6-luna";
            default -> "gpt-5.6-luna";
        };
    }
}
