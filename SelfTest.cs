using System.Text.Json;

namespace ToneIME;

internal static class SelfTest
{
    public static void Run()
    {
        var json = """
        {
          "primary": "明日、写真を送ってもらえる？",
          "alternatives": [{"label":"更柔和","text":"明日、時間があるときに写真を送ってもらえる？"}],
          "backTranslation": "明天能把照片发给我吗？",
          "detectedScene": "请求",
          "warnings": [],
          "preservedTokens": ["2026/08/01"]
        }
        """;

        var result = JsonSerializer.Deserialize<TranslateResult>(json)
                     ?? throw new InvalidOperationException("JSON 解析失败。");
        Assert(result.Primary.Contains("写真"), "主译文解析");
        Assert(result.Alternatives.Count == 1, "候选解析");

        var warnings = TranslationValidator.Validate(
            "请在 2026/08/01 前打开 https://example.com",
            "2026/08/01までに https://example.com を開いてください。");
        Assert(warnings.Count == 0, "数字和 URL 保留");

        warnings = TranslationValidator.Validate("价格是 1200 日元", "価格は千二百円です。");
        Assert(warnings.Count > 0, "数字丢失检测");

        var deduper = new RecentTextDeduper(3);
        Assert(deduper.TryAdd("こんにちは"), "首次消息");
        Assert(!deduper.TryAdd(" こんにちは "), "重复消息");
        Assert(JapaneseText.IsLikelyJapanese("了解"), "纯汉字日语检测");
        Assert(
            WindowsIntegration.NativeInputSize == (Environment.Is64BitProcess ? 40 : 28),
            "Win32 INPUT 结构尺寸");
        Assert(
            OpenAiCompatibleClient.IsRealtimeConversationModel("gpt-realtime-2.1-mini"),
            "Realtime 会话模型分流");
        Assert(
            !OpenAiCompatibleClient.IsRealtimeConversationModel("gpt-5.6-luna"),
            "常规模型保留 Chat Completions");
        Assert(
            !OpenAiCompatibleClient.IsRealtimeConversationModel("gpt-realtime-translate"),
            "音频翻译模型不进入文字会话");

        var ocrLines = OpenAiCompatibleClient.ParseOcrLines(
            """{"lines":["10:20","今日はありがとう","明天见","Settings"]}""");
        Assert(ocrLines.SequenceEqual(["今日はありがとう", "明天见"]), "OCR 行过滤");

        var lineRect = new WindowsIntegration.NativeRect
            { Left = 100, Top = 200, Right = 1100, Bottom = 1000 };
        Assert(
            LineReaderService.ResolveOcrRegion(new ScreenRegion(-500, 0, 200, 200), lineRect) ==
            new ScreenRegion(460, 272, 620, 624),
            "失效 OCR 框自动定位当前 LINE 聊天区");
    }

    private static void Assert(bool condition, string name)
    {
        if (!condition)
        {
            throw new InvalidOperationException($"自检失败：{name}");
        }
    }
}
