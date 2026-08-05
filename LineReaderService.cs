using System.Text.RegularExpressions;
using System.Windows.Automation;

namespace ToneIME;

internal sealed partial class LineReaderService : IAsyncDisposable
{
    private readonly RecentTextDeduper _deduper = new(200);
    private readonly WindowsOcrService _ocr = new();
    private CancellationTokenSource? _cancellation;
    private Task? _loop;
    private volatile bool _isRunning;
    private int _generation;

    public bool IsRunning => _isRunning;
    public bool OcrAvailable => _ocr.IsAvailable;

    public event Action<string>? TextDetected;
    public event Action<string>? StatusChanged;
    public event Action? Stopped;

    public void Remember(string text) => _deduper.TryAdd(text);

    public void Start(
        nint targetWindow,
        ScreenRegion? ocrRegion,
        Func<byte[], CancellationToken, Task<IReadOnlyList<string>>>? cloudOcr = null)
    {
        Stop();
        var generation = Interlocked.Increment(ref _generation);
        _cancellation = new CancellationTokenSource();
        _isRunning = true;
        _loop = Task.Run(() => RunAsync(targetWindow, ocrRegion, cloudOcr, generation, _cancellation.Token));
    }

    public void Stop()
    {
        Interlocked.Increment(ref _generation);
        _cancellation?.Cancel();
        _cancellation?.Dispose();
        _cancellation = null;
        if (_isRunning)
        {
            _isRunning = false;
            Stopped?.Invoke();
        }
    }

    private async Task RunAsync(
        nint targetWindow,
        ScreenRegion? ocrRegion,
        Func<byte[], CancellationToken, Task<IReadOnlyList<string>>>? cloudOcr,
        int generation,
        CancellationToken token)
    {
        var usedAutomaticRegion = false;
        if (WindowsIntegration.GetWindowRect(targetWindow, out var targetRect))
        {
            var resolvedRegion = ResolveOcrRegion(ocrRegion, targetRect);
            usedAutomaticRegion = !Equals(ocrRegion, resolvedRegion);
            ocrRegion = resolvedRegion;
        }

        var baseline = cloudOcr is null;
        var automationMisses = 0;
        var useOcr = cloudOcr is not null;
        var lastImageHash = 0UL;
        var lastNewText = DateTime.UtcNow;

        RaiseStatus(useOcr
            ? usedAutomaticRegion
                ? "旧 OCR 框不在当前 LINE 窗口，已自动定位聊天区 · OpenAI OCR 准备中…"
                : "OpenAI OCR 准备中…"
            : "正在连接 LINE 文本…");
        try
        {
            while (!token.IsCancellationRequested)
            {
                if (!WindowsIntegration.IsWindow(targetWindow))
                {
                    RaiseStatus("LINE 窗口已关闭，读屏停止。");
                    break;
                }

                IReadOnlyList<string> texts;
                if (!useOcr)
                {
                    texts = ReadAutomationText(targetWindow);
                    if (texts.Count == 0)
                    {
                        automationMisses++;
                        if (automationMisses >= 3)
                        {
                            if (ocrRegion is { IsValid: true } && _ocr.IsAvailable)
                            {
                                useOcr = true;
                                baseline = false;
                                RaiseStatus(usedAutomaticRegion
                                    ? "LINE 未公开消息文本，已自动定位聊天区并切换本机 OCR。"
                                    : "LINE 未公开消息文本，已切换本机 OCR。");
                            }
                            else
                            {
                                RaiseStatus(_ocr.IsAvailable
                                    ? "LINE 未公开消息文本，请停止后框选 OCR 区域。"
                                    : "LINE 未公开消息文本，且 Windows 日语 OCR 不可用。");
                            }
                        }
                    }
                    else
                    {
                        automationMisses = 0;
                        RaiseStatus("读取中 · UI Automation · 截图不上传");
                    }
                }
                else
                {
                    var capture = await _ocr.CaptureAsync(ocrRegion!, token);
                    if (capture.ImageHash == lastImageHash)
                    {
                        await Task.Delay(800, token);
                        continue;
                    }

                    lastImageHash = capture.ImageHash;
                    var localTexts = SplitOcrText(capture.Text);
                    if (cloudOcr is null)
                    {
                        texts = localTexts;
                        RaiseStatus($"读取中 · 本机 OCR · 识别 {texts.Count} 行 · 截图不上传");
                    }
                    else
                    {
                        RaiseStatus("OpenAI OCR 识别中 · 正在发送框选截图…");
                        try
                        {
                            texts = await cloudOcr(capture.PngBytes, token);
                            if (texts.Count == 0 && localTexts.Count > 0)
                            {
                                texts = localTexts;
                                RaiseStatus($"OpenAI OCR 返回 0 行 · 已回退本机 OCR · 识别 {texts.Count} 行");
                            }
                            else
                            {
                                RaiseStatus($"读取中 · OpenAI OCR · 识别 {texts.Count} 行 · 框选截图已发送");
                            }
                        }
                        catch (Exception ex) when (localTexts.Count > 0)
                        {
                            texts = localTexts;
                            RaiseStatus($"OpenAI OCR 失败：{ex.Message} · 已回退本机 OCR · 识别 {texts.Count} 行");
                        }
                    }
                }

                if (texts.Count > 0)
                {
                    var newlyAdded = new List<string>();
                    foreach (var text in texts)
                    {
                        if (_deduper.TryAdd(text))
                        {
                            newlyAdded.Add(text);
                        }
                    }

                    if (baseline)
                    {
                        // Seed visible text so menus and old messages are not sent to the model.
                        baseline = false;
                    }
                    else
                    {
                        foreach (var text in newlyAdded)
                        {
                            RaiseText(text);
                            lastNewText = DateTime.UtcNow;
                        }
                    }
                }

                if (DateTime.UtcNow - lastNewText > TimeSpan.FromMinutes(30))
                {
                    RaiseStatus("连续 30 分钟无新消息，读屏已自动停止。");
                    break;
                }

                await Task.Delay(900, token);
            }
        }
        catch (OperationCanceledException)
        {
            RaiseStatus("读屏已停止。");
        }
        catch (Exception ex)
        {
            RaiseStatus($"读屏停止：{ex.Message}");
        }
        finally
        {
            if (generation == Volatile.Read(ref _generation))
            {
                _isRunning = false;
                Stopped?.Invoke();
            }
        }
    }

    private static IReadOnlyList<string> ReadAutomationText(nint targetWindow)
    {
        try
        {
            var root = AutomationElement.FromHandle(targetWindow);
            if (!WindowsIntegration.GetWindowRect(targetWindow, out var windowRect))
            {
                return [];
            }

            var windowWidth = Math.Max(1, windowRect.Right - windowRect.Left);
            var windowHeight = Math.Max(1, windowRect.Bottom - windowRect.Top);
            var contentLeft = windowRect.Left + windowWidth * 0.22;
            var contentTop = windowRect.Top + windowHeight * 0.08;
            var contentRight = windowRect.Right - windowWidth * 0.02;
            var contentBottom = windowRect.Bottom - windowHeight * 0.17;
            var elements = root.FindAll(TreeScope.Descendants, Condition.TrueCondition);
            var found = new List<(double Top, double Left, string Text)>();
            var limit = Math.Min(elements.Count, 2500);
            for (var index = 0; index < limit; index++)
            {
                try
                {
                    var element = elements[index];
                    var name = element.Current.Name?.Trim();
                    if (string.IsNullOrWhiteSpace(name) || !JapaneseText.IsLikelyJapanese(name))
                    {
                        continue;
                    }

                    var rectangle = element.Current.BoundingRectangle;
                    if (rectangle.IsEmpty)
                    {
                        continue;
                    }

                    if (rectangle.Left < contentLeft ||
                        rectangle.Top < contentTop ||
                        rectangle.Right > contentRight ||
                        rectangle.Bottom > contentBottom)
                    {
                        continue;
                    }

                    found.Add((rectangle.Top, rectangle.Left, name));
                }
                catch (ElementNotAvailableException)
                {
                    // LINE may recycle message elements during a scan.
                }
            }

            return found
                .OrderBy(x => x.Top)
                .ThenBy(x => x.Left)
                .Select(x => x.Text)
                .Distinct(StringComparer.Ordinal)
                .ToList();
        }
        catch
        {
            return [];
        }
    }

    private static IReadOnlyList<string> SplitOcrText(string text) =>
        text.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Where(line => !TimestampRegex().IsMatch(line))
            .Where(JapaneseText.IsLikelyJapanese)
            .Distinct(StringComparer.Ordinal)
            .ToList();

    internal static ScreenRegion ResolveOcrRegion(
        ScreenRegion? selected,
        WindowsIntegration.NativeRect window)
    {
        if (selected is { IsValid: true } &&
            selected.X >= window.Left &&
            selected.Y >= window.Top &&
            (long)selected.X + selected.Width <= window.Right &&
            (long)selected.Y + selected.Height <= window.Bottom)
        {
            return selected;
        }

        var width = Math.Max(1, window.Right - window.Left);
        var height = Math.Max(1, window.Bottom - window.Top);
        // ponytail: LINE desktop layout heuristic; replace with pane geometry if LINE exposes stable UIA bounds.
        var left = window.Left + width * 36 / 100;
        var top = window.Top + height * 9 / 100;
        var right = window.Right - width * 2 / 100;
        var bottom = window.Bottom - height * 13 / 100;
        return new ScreenRegion(left, top, right - left, bottom - top);
    }

    private void RaiseText(string text) => TextDetected?.Invoke(text);
    private void RaiseStatus(string status) => StatusChanged?.Invoke(status);

    public async ValueTask DisposeAsync()
    {
        Stop();
        if (_loop is not null)
        {
            try
            {
                await _loop;
            }
            catch (OperationCanceledException)
            {
                // Expected on shutdown.
            }
        }
    }

    [GeneratedRegex(@"^\s*\d{1,2}:\d{2}\s*$")]
    private static partial Regex TimestampRegex();
}
