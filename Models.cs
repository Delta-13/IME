using System.Text.Json.Serialization;

namespace ToneIME;

public sealed class AppSettings
{
    public string Provider { get; set; } = ApiProviders.OpenAi;
    public string BaseUrl { get; set; } = "https://api.openai.com/v1";
    public string Model { get; set; } = "gpt-5.6-luna";
    public string Direction { get; set; } = "中→日";
    public string Relation { get; set; } = "朋友";
    public string Scene { get; set; } = "自动";
    public int Politeness { get; set; } = 3;
    public int Warmth { get; set; } = 3;
    public int Directness { get; set; } = 3;
    public string PreferredExamples { get; set; } = "";
    public string AvoidExpressions { get; set; } = "";
    public bool ScreenReadingConsent { get; set; }
    public ScreenRegion? OcrRegion { get; set; }
}

public sealed record ProviderPreset(
    string Id,
    string DisplayName,
    string BaseUrl,
    string DefaultModel,
    string[] Models,
    string Hint);

public static class ApiProviders
{
    public const string OpenAi = "openai";
    public const string Claude = "claude";
    public const string Qwen = "qwen";
    public const string Kimi = "kimi";
    public const string MiniMax = "minimax";
    public const string DeepSeek = "deepseek";
    public const string Google = "google";
    public const string Custom = "custom";

    public static IReadOnlyList<ProviderPreset> Presets { get; } =
    [
        new(
            OpenAi,
            "OpenAI",
            "https://api.openai.com/v1",
            "gpt-5.6-luna",
            ["gpt-5.6-luna", "gpt-4.1-mini", "gpt-5.6-terra", "gpt-realtime-2.1-mini", "gpt-realtime-2.1"],
            "OpenAI Chat Completions；Realtime 文字可使用 gpt-realtime-2.1-mini。"),
        new(
            Claude,
            "Claude (Anthropic)",
            "https://api.anthropic.com/v1",
            "claude-sonnet-4-5",
            ["claude-sonnet-4-5", "claude-opus-5"],
            "使用 Claude Messages API；API Key 通过 x-api-key 发送。"),
        new(
            Qwen,
            "Qwen (DashScope)",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen-plus",
            ["qwen-plus", "qwen-turbo", "qwen-max"],
            "使用 DashScope OpenAI 兼容模式；可替换为你的工作区专属 HTTPS 地址。"),
        new(
            Kimi,
            "Kimi (Moonshot)",
            "https://api.moonshot.ai/v1",
            "kimi-k3",
            ["kimi-k3", "kimi-k2.5"],
            "使用 Moonshot Chat Completions API。"),
        new(
            MiniMax,
            "MiniMax",
            "https://api.minimax.io/v1",
            "MiniMax-M2.7",
            ["MiniMax-M2.7", "MiniMax-M2.7-highspeed", "MiniMax-M2.5"],
            "使用 MiniMax OpenAI 兼容 API。"),
        new(
            DeepSeek,
            "DeepSeek",
            "https://api.deepseek.com",
            "deepseek-v4-flash",
            ["deepseek-v4-flash", "deepseek-v4-pro"],
            "使用 DeepSeek OpenAI Chat Completions API。"),
        new(
            Google,
            "Google AI (Gemini)",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-3.6-flash",
            ["gemini-3.6-flash", "gemini-3.5-flash"],
            "使用 Gemini 的 OpenAI 兼容端点。"),
        new(
            Custom,
            "Custom / OpenAI compatible",
            "https://api.openai.com/v1",
            "gpt-5.6-luna",
            [],
            "使用任何兼容 OpenAI Chat Completions 的 HTTPS 端点。")
    ];

    public static ProviderPreset Get(string? provider) =>
        Presets.FirstOrDefault(item => item.Id.Equals(provider, StringComparison.OrdinalIgnoreCase))
        ?? Presets[0];

    public static string Normalize(string? provider) => Get(provider).Id;

    public static bool UsesClaudeMessages(string? provider) =>
        Claude.Equals(Normalize(provider), StringComparison.Ordinal);

    public static bool SupportsNoStore(string? provider) =>
        OpenAi.Equals(Normalize(provider), StringComparison.Ordinal);

    public static bool SupportsJsonResponseFormat(string? provider)
    {
        var normalized = Normalize(provider);
        return OpenAi.Equals(normalized, StringComparison.Ordinal) ||
               DeepSeek.Equals(normalized, StringComparison.Ordinal);
    }
}
public sealed record ScreenRegion(int X, int Y, int Width, int Height)
{
    [JsonIgnore]
    public bool IsValid => Width > 10 && Height > 10;
}

public sealed class TranslateRequest
{
    public long Revision { get; init; }
    public required string Direction { get; init; }
    public required string SourceText { get; init; }
    public required string Relation { get; init; }
    public required string Scene { get; init; }
    public required ToneStyle Style { get; init; }
    public string? QuotedContext { get; init; }
}

public sealed class ToneStyle
{
    public int Politeness { get; init; }
    public int Warmth { get; init; }
    public int Directness { get; init; }
    public string[] PreferredExamples { get; init; } = [];
    public string[] AvoidExpressions { get; init; } = [];
}

public sealed class TranslateResult
{
    [JsonPropertyName("primary")]
    public string Primary { get; set; } = "";

    [JsonPropertyName("alternatives")]
    public List<TranslationAlternative> Alternatives { get; set; } = [];

    [JsonPropertyName("backTranslation")]
    public string BackTranslation { get; set; } = "";

    [JsonPropertyName("detectedScene")]
    public string DetectedScene { get; set; } = "";

    [JsonPropertyName("warnings")]
    public List<string> Warnings { get; set; } = [];

    [JsonPropertyName("preservedTokens")]
    public List<string> PreservedTokens { get; set; } = [];
}

public sealed class TranslationAlternative
{
    [JsonPropertyName("label")]
    public string Label { get; set; } = "";

    [JsonPropertyName("text")]
    public string Text { get; set; } = "";
}

public sealed record TranslationCandidate(string Label, string Text)
{
    public override string ToString() => $"{Label}　{Text}";
}

public sealed record ReaderItem(DateTime Timestamp, string Source, string Translation)
{
    public string Display => $"{Timestamp:HH:mm}\n{Source}\n→ {Translation}";
}
