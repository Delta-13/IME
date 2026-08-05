using System.Text.RegularExpressions;

namespace ToneIME;

internal static partial class TranslationValidator
{
    public static List<string> Validate(string source, string translation)
    {
        var warnings = new List<string>();
        foreach (Match match in ImportantTokenRegex().Matches(source))
        {
            var token = match.Value.TrimEnd('.', ',', '，', '。');
            if (!translation.Contains(token, StringComparison.Ordinal))
            {
                warnings.Add($"请确认关键内容是否保留：{token}");
            }
        }

        if (ChineseNegationRegex().IsMatch(source) && !JapaneseNegationRegex().IsMatch(translation))
        {
            warnings.Add("原文含否定表达，请检查译文是否保持否定含义。");
        }

        return warnings.Distinct(StringComparer.Ordinal).ToList();
    }

    [GeneratedRegex(@"https?://[^\s]+|\d+(?:[./:\-]\d+)+|(?<!\d)\d+(?:[.,]\d+)?(?!\d)", RegexOptions.IgnoreCase)]
    private static partial Regex ImportantTokenRegex();

    [GeneratedRegex(@"不|没|無|不能|不要|没有|別|别")]
    private static partial Regex ChineseNegationRegex();

    [GeneratedRegex(@"ない|ません|なかった|ませんでした|ず|無理|できな|禁止|不要")]
    private static partial Regex JapaneseNegationRegex();
}

internal sealed class RecentTextDeduper(int capacity)
{
    private readonly Queue<string> _order = new();
    private readonly HashSet<string> _seen = new(StringComparer.Ordinal);

    public bool TryAdd(string text)
    {
        var normalized = Normalize(text);
        if (normalized.Length < 2 || !_seen.Add(normalized))
        {
            return false;
        }

        _order.Enqueue(normalized);
        while (_order.Count > capacity)
        {
            _seen.Remove(_order.Dequeue());
        }

        return true;
    }

    private static string Normalize(string text) =>
        Regex.Replace(text.Trim(), @"\s+", " ");
}

internal static partial class JapaneseText
{
    public static bool IsLikelyJapanese(string text) =>
        text.Length is >= 2 and <= 800 && JapaneseCharacterRegex().IsMatch(text);

    [GeneratedRegex(@"[\p{IsHiragana}\p{IsKatakana}一-龯々〆ヵヶ]")]
    private static partial Regex JapaneseCharacterRegex();
}
