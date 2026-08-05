using System.Text.Json.Serialization;

namespace ToneIME;

public sealed class AppSettings
{
    public string BaseUrl { get; set; } = "https://api.openai.com/v1";
    public string Model { get; set; } = "gpt-4.1-mini";
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
