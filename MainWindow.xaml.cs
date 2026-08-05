using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Threading;
using Microsoft.Win32;

namespace ToneIME;

public partial class MainWindow : Window
{
    private static readonly string[] Directions = ["中→日", "日→中"];
    private static readonly string[] Relations = ["长辈", "领导", "平辈", "朋友", "好朋友", "情侣"];
    private static readonly string[] Scenes = ["自动", "闲聊", "请求", "道歉", "感谢", "拒绝", "关心"];
    private static readonly string[] Models =
        ["gpt-5.6-luna", "gpt-realtime-2.1-mini", "gpt-realtime-2.1", "gpt-4.1-mini", "gpt-5.6-terra"];

    private readonly DispatcherTimer _debounceTimer;
    private readonly ObservableCollection<ReaderItem> _readerItems = [];
    private readonly LineReaderService _reader = new();
    private readonly SemaphoreSlim _readerTranslationLock = new(1, 1);
    private AppSettings _settings;
    private string _apiKey;
    private InputTarget? _inputTarget;
    private CancellationTokenSource? _translationCancellation;
    private long _revision;
    private long _readerRevision;
    private bool _isComposing;
    private bool _loadingControls = true;
    private string? _quotedContext;

    public MainWindow()
    {
        InitializeComponent();

        _settings = SettingsStore.Load();
        _apiKey = SettingsStore.LoadApiKey();
        DirectionBox.ItemsSource = Directions;
        RelationBox.ItemsSource = Relations;
        SceneBox.ItemsSource = Scenes;
        ModelBox.ItemsSource = Models;
        ReaderList.ItemsSource = _readerItems;
        LoadControls();

        _debounceTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(600) };
        _debounceTimer.Tick += async (_, _) =>
        {
            _debounceTimer.Stop();
            if (!_isComposing)
            {
                await TranslateCurrentAsync();
            }
        };

        TextCompositionManager.AddPreviewTextInputStartHandler(SourceTextBox, OnCompositionStart);
        TextCompositionManager.AddPreviewTextInputUpdateHandler(SourceTextBox, OnCompositionUpdate);
        TextCompositionManager.AddPreviewTextInputHandler(SourceTextBox, OnCompositionComplete);

        _reader.TextDetected += text => _ = TranslateIncomingAsync(text);
        _reader.StatusChanged += status =>
            Dispatcher.BeginInvoke(() =>
            {
                ReaderStatusText.Text = status;
            });
        _reader.Stopped += () => Dispatcher.BeginInvoke(() =>
        {
            StartReaderButton.IsEnabled = true;
            CloudOcrButton.IsEnabled = true;
            StopReaderButton.IsEnabled = false;
        });
        SystemEvents.SessionSwitch += OnSessionSwitch;

        if (string.IsNullOrWhiteSpace(_apiKey) && _settings.BaseUrl.Contains("openai.com", StringComparison.OrdinalIgnoreCase))
        {
            MainTabs.SelectedIndex = 1;
            AppStatusText.Text = "请先填写 API Key 并测试连接。";
        }
    }

    private void LoadControls()
    {
        BaseUrlBox.Text = _settings.BaseUrl;
        ModelBox.Text = _settings.Model;
        ApiKeyBox.Password = _apiKey;
        DirectionBox.SelectedItem = Directions.Contains(_settings.Direction) ? _settings.Direction : Directions[0];
        RelationBox.SelectedItem = Relations.Contains(_settings.Relation) ? _settings.Relation : "朋友";
        SceneBox.SelectedItem = Scenes.Contains(_settings.Scene) ? _settings.Scene : "自动";
        PolitenessSlider.Value = _settings.Politeness;
        WarmthSlider.Value = _settings.Warmth;
        DirectnessSlider.Value = _settings.Directness;
        PreferredExamplesBox.Text = _settings.PreferredExamples;
        AvoidExpressionsBox.Text = _settings.AvoidExpressions;
        ScreenConsentBox.IsChecked = _settings.ScreenReadingConsent;
        UpdateStyleSummary();
        _loadingControls = false;
    }

    private void OnSourceInitialized(object? sender, EventArgs e)
    {
        var source = HwndSource.FromHwnd(new WindowInteropHelper(this).Handle);
        source?.AddHook(WindowMessageHook);
        if (!WindowsIntegration.RegisterGlobalHotKey(this))
        {
            AppStatusText.Text = "无法注册 Ctrl+Alt+J；该快捷键可能已被占用。";
        }
    }

    private nint WindowMessageHook(nint window, int message, nint wParam, nint lParam, ref bool handled)
    {
        if (message == WindowsIntegration.HotKeyMessage && wParam.ToInt32() == WindowsIntegration.HotKeyId)
        {
            _inputTarget = WindowsIntegration.CaptureCurrentTarget(this) ?? _inputTarget;
            ShowTranslator();
            handled = true;
        }

        return 0;
    }

    private void ShowTranslator()
    {
        Show();
        WindowState = WindowState.Normal;
        MainTabs.SelectedIndex = 0;
        if (_inputTarget is not null)
        {
            WindowsIntegration.PositionNear(this, _inputTarget.WindowHandle);
        }

        Activate();
        SourceTextBox.Focus();
        SourceTextBox.CaretIndex = SourceTextBox.Text.Length;
    }

    private void OnSourceTextChanged(object sender, System.Windows.Controls.TextChangedEventArgs e)
    {
        if (_loadingControls)
        {
            return;
        }

        InvalidateOutgoingResult();
        _debounceTimer.Stop();
        if (_isComposing || SourceTextBox.Text.Trim().Length < 2)
        {
            return;
        }

        var delay = EndsSentence(SourceTextBox.Text) ? 80 : 600;
        _debounceTimer.Interval = TimeSpan.FromMilliseconds(delay);
        _debounceTimer.Start();
    }

    private void OnCompositionStart(object sender, TextCompositionEventArgs e)
    {
        _isComposing = true;
        _debounceTimer.Stop();
    }

    private void OnCompositionUpdate(object sender, TextCompositionEventArgs e)
    {
        _isComposing = true;
        _debounceTimer.Stop();
    }

    private void OnCompositionComplete(object sender, TextCompositionEventArgs e)
    {
        _isComposing = false;
        _debounceTimer.Stop();
        _debounceTimer.Interval = TimeSpan.FromMilliseconds(600);
        _debounceTimer.Start();
    }

    private async void OnTranslate(object sender, RoutedEventArgs e) => await TranslateCurrentAsync();

    private async Task TranslateCurrentAsync()
    {
        var source = SourceTextBox.Text.Trim();
        if (source.Length < 2)
        {
            return;
        }

        _debounceTimer.Stop();
        _translationCancellation?.Cancel();
        _translationCancellation?.Dispose();
        _translationCancellation = new CancellationTokenSource();
        var revision = Interlocked.Increment(ref _revision);
        var request = BuildRequest(revision, source, _quotedContext);

        TranslateButton.IsEnabled = false;
        InsertButton.IsEnabled = false;
        TranslationStatusText.Text = "正在翻译…";
        AppStatusText.Text = "模型请求中；不会自动回填或发送。";

        try
        {
            var client = new OpenAiCompatibleClient(ReadSettingsFromControls(), ApiKeyBox.Password);
            var result = await client.TranslateAsync(request, _translationCancellation.Token);
            if (revision != Volatile.Read(ref _revision))
            {
                return;
            }

            ShowTranslationResult(result);
            TranslationStatusText.Text = string.IsNullOrWhiteSpace(result.DetectedScene)
                ? "翻译完成"
                : $"翻译完成 · 场景：{result.DetectedScene}";
            AppStatusText.Text = "请检查回译，再选择译文插入。";
        }
        catch (OperationCanceledException)
        {
            TranslationStatusText.Text = "已取消旧请求";
        }
        catch (Exception ex)
        {
            TranslationStatusText.Text = $"翻译失败：{ex.Message}";
            AppStatusText.Text = "草稿已保留，可检查设置后重试。";
        }
        finally
        {
            if (revision == Volatile.Read(ref _revision))
            {
                TranslateButton.IsEnabled = true;
            }
        }
    }

    private TranslateRequest BuildRequest(long revision, string source, string? quotedContext) => new()
    {
        Revision = revision,
        Direction = DirectionBox.SelectedItem?.ToString() ?? "中→日",
        SourceText = source,
        Relation = RelationBox.SelectedItem?.ToString() ?? "朋友",
        Scene = SceneBox.SelectedItem?.ToString() ?? "自动",
        Style = new ToneStyle
        {
            Politeness = (int)PolitenessSlider.Value,
            Warmth = (int)WarmthSlider.Value,
            Directness = (int)DirectnessSlider.Value,
            PreferredExamples = SplitLines(PreferredExamplesBox.Text, 3),
            AvoidExpressions = SplitLines(AvoidExpressionsBox.Text, 12)
        },
        QuotedContext = quotedContext
    };

    private void ShowTranslationResult(TranslateResult result)
    {
        var candidates = new List<TranslationCandidate>
        {
            new("推荐", result.Primary)
        };
        candidates.AddRange(result.Alternatives.Select(x =>
            new TranslationCandidate(string.IsNullOrWhiteSpace(x.Label) ? "备选" : x.Label, x.Text)));

        CandidateList.ItemsSource = candidates;
        CandidateList.SelectedIndex = 0;
        BackTranslationText.Text = string.IsNullOrWhiteSpace(result.BackTranslation) ? "—" : result.BackTranslation;
        WarningBorder.Visibility = result.Warnings.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        WarningText.Text = string.Join("\n", result.Warnings.Select(x => $"• {x}"));
        InsertButton.IsEnabled = candidates.Count > 0;
    }

    private async void OnInsert(object sender, RoutedEventArgs e) => await InsertSelectedAsync();

    private async Task InsertSelectedAsync()
    {
        if (CandidateList.SelectedItem is not TranslationCandidate candidate)
        {
            return;
        }

        var target = _inputTarget;
        if (target is null || !WindowsIntegration.IsWindow(target.WindowHandle))
        {
            var lineWindow = WindowsIntegration.FindLineWindow();
            if (lineWindow == 0)
            {
                AppStatusText.Text = "未找到 LINE 窗口。请先将光标放到 LINE 输入框，再按 Ctrl+Alt+J。";
                return;
            }

            target = WindowsIntegration.CreateLineTarget(lineWindow);
        }

        InsertButton.IsEnabled = false;
        _reader.Remember(candidate.Text);
        Hide();
        var inserted = await WindowsIntegration.InsertTextAsync(target, candidate.Text);
        if (!inserted)
        {
            ShowTranslator();
            Clipboard.SetText(candidate.Text);
            AppStatusText.Text = "自动回填失败，译文已复制；请在 LINE 中粘贴。";
            InsertButton.IsEnabled = true;
            return;
        }

        AppStatusText.Text = "译文已插入 LINE；仍需你亲自发送。";
        SourceTextBox.Clear();
        CandidateList.ItemsSource = null;
        BackTranslationText.Text = "—";
        WarningBorder.Visibility = Visibility.Collapsed;
        ClearQuotedContext();
    }

    private void OnSourcePreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter && Keyboard.Modifiers.HasFlag(ModifierKeys.Control))
        {
            e.Handled = true;
            _ = InsertSelectedAsync();
        }
        else if (e.Key == Key.Escape)
        {
            Hide();
            e.Handled = true;
        }
    }

    private void OnCopyCandidate(object sender, RoutedEventArgs e)
    {
        if (CandidateList.SelectedItem is TranslationCandidate candidate)
        {
            Clipboard.SetText(candidate.Text);
            AppStatusText.Text = "所选译文已复制。";
        }
    }

    private void OnClear(object sender, RoutedEventArgs e)
    {
        _translationCancellation?.Cancel();
        SourceTextBox.Clear();
        CandidateList.ItemsSource = null;
        BackTranslationText.Text = "—";
        WarningBorder.Visibility = Visibility.Collapsed;
        ClearQuotedContext();
        TranslationStatusText.Text = "输入后停顿 600ms 自动翻译";
    }

    private void OnToneOptionChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (_loadingControls)
        {
            return;
        }

        UpdateStyleSummary();
        InvalidateOutgoingResult();
        if (SourceTextBox.Text.Trim().Length >= 2)
        {
            _debounceTimer.Stop();
            _debounceTimer.Interval = TimeSpan.FromMilliseconds(150);
            _debounceTimer.Start();
        }
    }

    private void OnStyleSliderChanged(object sender, RoutedPropertyChangedEventArgs<double> e)
    {
        if (!_loadingControls)
        {
            UpdateStyleSummary();
            InvalidateOutgoingResult();
            if (SourceTextBox.Text.Trim().Length >= 2)
            {
                _debounceTimer.Stop();
                _debounceTimer.Interval = TimeSpan.FromMilliseconds(150);
                _debounceTimer.Start();
            }
        }
    }

    private void InvalidateOutgoingResult()
    {
        _translationCancellation?.Cancel();
        Interlocked.Increment(ref _revision);
        TranslateButton.IsEnabled = true;
        InsertButton.IsEnabled = false;
        CandidateList.ItemsSource = null;
        BackTranslationText.Text = "—";
        WarningBorder.Visibility = Visibility.Collapsed;
    }

    private void UpdateStyleSummary()
    {
        if (StyleSummaryText is null)
        {
            return;
        }

        StyleSummaryText.Text =
            $"{RelationBox.SelectedItem ?? "朋友"} · 礼貌 {(int)PolitenessSlider.Value}/5 · 亲密 {(int)WarmthSlider.Value}/5 · 直接 {(int)DirectnessSlider.Value}/5";
    }

    private async void OnTestConnection(object sender, RoutedEventArgs e)
    {
        SaveSettings(showStatus: false);
        AppStatusText.Text = "正在测试模型连接…";
        var stopwatch = Stopwatch.StartNew();
        try
        {
            var client = new OpenAiCompatibleClient(_settings, _apiKey);
            var result = await client.TranslateAsync(
                new TranslateRequest
                {
                    Revision = 0,
                    Direction = "中→日",
                    SourceText = "明天见。",
                    Relation = "朋友",
                    Scene = "闲聊",
                    Style = new ToneStyle { Politeness = 3, Warmth = 3, Directness = 3 }
                },
                CancellationToken.None);
            AppStatusText.Text = $"连接成功 · {stopwatch.ElapsedMilliseconds} ms：{result.Primary}";
        }
        catch (Exception ex)
        {
            AppStatusText.Text = $"连接失败 · {stopwatch.ElapsedMilliseconds} ms：{ex.Message}";
        }
    }

    private void OnSaveSettings(object sender, RoutedEventArgs e) => SaveSettings(showStatus: true);

    private void SaveSettings(bool showStatus)
    {
        _settings = ReadSettingsFromControls();
        _apiKey = ApiKeyBox.Password;
        SettingsStore.Save(_settings, _apiKey);
        if (showStatus)
        {
            AppStatusText.Text = "设置已保存；API Key 位于 Windows 凭据管理器。";
        }
    }

    private AppSettings ReadSettingsFromControls() => new()
    {
        BaseUrl = BaseUrlBox.Text.Trim(),
        Model = ModelBox.Text.Trim(),
        Direction = DirectionBox.SelectedItem?.ToString() ?? "中→日",
        Relation = RelationBox.SelectedItem?.ToString() ?? "朋友",
        Scene = SceneBox.SelectedItem?.ToString() ?? "自动",
        Politeness = (int)PolitenessSlider.Value,
        Warmth = (int)WarmthSlider.Value,
        Directness = (int)DirectnessSlider.Value,
        PreferredExamples = PreferredExamplesBox.Text.Trim(),
        AvoidExpressions = AvoidExpressionsBox.Text.Trim(),
        ScreenReadingConsent = ScreenConsentBox.IsChecked == true,
        OcrRegion = _settings.OcrRegion
    };

    private void OnStartReader(object sender, RoutedEventArgs e) => StartReader(useCloudOcr: false);

    private void OnStartCloudOcr(object sender, RoutedEventArgs e) => StartReader(useCloudOcr: true);

    private void StartReader(bool useCloudOcr)
    {
        SaveSettings(showStatus: false);
        if (!_settings.ScreenReadingConsent)
        {
            MainTabs.SelectedIndex = 1;
            AppStatusText.Text = "请先阅读并勾选读屏说明。";
            return;
        }

        var lineWindow = _inputTarget is not null &&
                         WindowsIntegration.IsWindow(_inputTarget.WindowHandle) &&
                         WindowsIntegration.GetWindowTitle(_inputTarget.WindowHandle).Contains("LINE", StringComparison.OrdinalIgnoreCase)
            ? _inputTarget.WindowHandle
            : WindowsIntegration.FindLineWindow();
        if (lineWindow == 0)
        {
            ReaderStatusText.Text = "未找到正在运行的 LINE 窗口。";
            return;
        }

        Func<byte[], CancellationToken, Task<IReadOnlyList<string>>>? cloudOcr = null;
        if (useCloudOcr)
        {
            var client = new OpenAiCompatibleClient(_settings, _apiKey);
            cloudOcr = client.RecognizeChatImageAsync;
        }

        _reader.Start(lineWindow, _settings.OcrRegion, cloudOcr);
        StartReaderButton.IsEnabled = false;
        CloudOcrButton.IsEnabled = false;
        StopReaderButton.IsEnabled = true;
        ReaderStatusText.Text = useCloudOcr
            ? "OpenAI OCR 本次会话已开始；只发送框选截图。"
            : "本次会话已开始；正在检测 LINE 的可访问文本。";
    }

    private void OnStopReader(object sender, RoutedEventArgs e)
    {
        _reader.Stop();
        StartReaderButton.IsEnabled = true;
        CloudOcrButton.IsEnabled = true;
        StopReaderButton.IsEnabled = false;
        ReaderStatusText.Text = "读屏已停止。";
    }

    private void OnSelectOcrRegion(object sender, RoutedEventArgs e)
    {
        _reader.Stop();
        Hide();
        var selector = new RegionSelectorWindow();
        var accepted = selector.ShowDialog() == true;
        Show();
        Activate();

        if (!accepted || selector.SelectedRegion is not { IsValid: true } region)
        {
            AppStatusText.Text = "未更改 OCR 区域。";
            return;
        }

        _settings = ReadSettingsFromControls();
        _settings.OcrRegion = region;
        SettingsStore.Save(_settings, ApiKeyBox.Password);
        ReaderStatusText.Text = $"OCR 区域已保存：{region.Width}×{region.Height}。点击“开始本次会话”。";
    }

    private async Task TranslateIncomingAsync(string source)
    {
        await _readerTranslationLock.WaitAsync();
        try
        {
            var settings = _settings;
            var client = new OpenAiCompatibleClient(settings, _apiKey);
            var result = await client.TranslateAsync(
                new TranslateRequest
                {
                    Revision = Interlocked.Increment(ref _readerRevision),
                    Direction = "自动识别：中文↔日文",
                    SourceText = source,
                    Relation = _settings.Relation,
                    Scene = "自动",
                    Style = new ToneStyle
                    {
                        Politeness = _settings.Politeness,
                        Warmth = _settings.Warmth,
                        Directness = _settings.Directness
                    }
                },
                CancellationToken.None);
            await Dispatcher.InvokeAsync(() =>
            {
                _readerItems.Add(new ReaderItem(DateTime.Now, source, result.Primary));
                while (_readerItems.Count > 100)
                {
                    _readerItems.RemoveAt(0);
                }

                ReaderList.ScrollIntoView(_readerItems[^1]);
            });
        }
        catch (Exception ex)
        {
            await Dispatcher.InvokeAsync(() => ReaderStatusText.Text = $"消息翻译失败：{ex.Message}");
        }
        finally
        {
            _readerTranslationLock.Release();
        }
    }

    private void OnReplyToReaderItem(object sender, RoutedEventArgs e)
    {
        if (ReaderList.SelectedItem is not ReaderItem item)
        {
            AppStatusText.Text = "请先选择一条消息。";
            return;
        }

        UseReplyItem(item);
    }

    private void OnReplyReaderItem(object sender, RoutedEventArgs e)
    {
        if (sender is FrameworkElement { Tag: ReaderItem item })
        {
            UseReplyItem(item);
        }
    }

    private void UseReplyItem(ReaderItem item)
    {
        _quotedContext = item.Source;
        QuotedContextText.Text = item.Source;
        QuotedContextBorder.Visibility = Visibility.Visible;
        DirectionBox.SelectedItem = "中→日";
        SourceTextBox.Clear();
        SourceTextBox.Focus();
        AppStatusText.Text = "已引用所选日语消息；请输入中文回复。";
    }

    private void OnClearQuote(object sender, RoutedEventArgs e) => ClearQuotedContext();

    private void ClearQuotedContext()
    {
        _quotedContext = null;
        QuotedContextText.Text = "";
        QuotedContextBorder.Visibility = Visibility.Collapsed;
    }

    private void OnClearReader(object sender, RoutedEventArgs e) => _readerItems.Clear();

    private void OnSessionSwitch(object sender, SessionSwitchEventArgs e)
    {
        if (e.Reason == SessionSwitchReason.SessionLock)
        {
            Dispatcher.BeginInvoke(() =>
            {
                _reader.Stop();
                StartReaderButton.IsEnabled = true;
                CloudOcrButton.IsEnabled = true;
                StopReaderButton.IsEnabled = false;
                ReaderStatusText.Text = "屏幕已锁定，读屏自动停止。";
            });
        }
    }

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        _debounceTimer.Stop();
        _translationCancellation?.Cancel();
        _reader.Stop();
        SystemEvents.SessionSwitch -= OnSessionSwitch;
        WindowsIntegration.UnregisterGlobalHotKey(this);
        try
        {
            SaveSettings(showStatus: false);
        }
        catch
        {
            // Closing must not be blocked by a settings write failure.
        }
    }

    private static string[] SplitLines(string text, int maximum) =>
        text.Split(['\r', '\n'], StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Take(maximum)
            .ToArray();

    private static bool EndsSentence(string text)
    {
        var trimmed = text.TrimEnd();
        return trimmed.Length > 0 && "。！？!?.".Contains(trimmed[^1]);
    }
}
