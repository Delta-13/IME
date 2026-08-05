using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows;
using System.Windows.Automation;
using System.Windows.Interop;
using System.Windows.Media;

namespace ToneIME;

internal sealed record InputTarget(nint WindowHandle, AutomationElement? FocusedElement);

internal static class WindowsIntegration
{
    public const int HotKeyId = 0x5449;
    public const int HotKeyMessage = 0x0312;
    private const uint ModAlt = 0x0001;
    private const uint ModControl = 0x0002;
    private const uint VkJ = 0x4A;
    private const uint InputKeyboard = 1;
    private const uint KeyUp = 0x0002;
    private const ushort VkControl = 0x11;
    private const ushort VkV = 0x56;

    [StructLayout(LayoutKind.Sequential)]
    internal struct NativeRect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct Input
    {
        public uint Type;
        public InputUnion Union;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)]
        public KeyboardInput Keyboard;

        [FieldOffset(0)]
        public MouseInput Mouse;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct KeyboardInput
    {
        public ushort VirtualKey;
        public ushort ScanCode;
        public uint Flags;
        public uint Time;
        public nint ExtraInfo;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MouseInput
    {
        public int X;
        public int Y;
        public uint MouseData;
        public uint Flags;
        public uint Time;
        public nint ExtraInfo;
    }

    private delegate bool EnumWindowsProc(nint window, nint parameter);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool RegisterHotKey(nint window, int id, uint modifiers, uint virtualKey);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnregisterHotKey(nint window, int id);

    [DllImport("user32.dll")]
    internal static extern nint GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(nint window);

    [DllImport("user32.dll")]
    internal static extern bool IsWindow(nint window);

    [DllImport("user32.dll")]
    internal static extern bool IsWindowVisible(nint window);

    [DllImport("user32.dll")]
    internal static extern bool GetWindowRect(nint window, out NativeRect rect);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(nint window, StringBuilder text, int count);

    [DllImport("user32.dll")]
    private static extern int GetWindowTextLength(nint window);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(nint window, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsProc callback, nint parameter);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputCount, Input[] inputs, int inputSize);

    internal static int NativeInputSize => Marshal.SizeOf<Input>();

    public static bool RegisterGlobalHotKey(Window window)
    {
        var handle = new WindowInteropHelper(window).Handle;
        return RegisterHotKey(handle, HotKeyId, ModControl | ModAlt, VkJ);
    }

    public static void UnregisterGlobalHotKey(Window window)
    {
        var handle = new WindowInteropHelper(window).Handle;
        if (handle != 0)
        {
            UnregisterHotKey(handle, HotKeyId);
        }
    }

    public static InputTarget? CaptureCurrentTarget(Window ownWindow)
    {
        var own = new WindowInteropHelper(ownWindow).Handle;
        var foreground = GetForegroundWindow();
        if (foreground == 0 || foreground == own || !IsLineWindow(foreground))
        {
            return null;
        }

        AutomationElement? focused = null;
        try
        {
            focused = AutomationElement.FocusedElement;
        }
        catch
        {
            // Some elevated or protected windows do not expose automation.
        }

        return new InputTarget(foreground, FindLineInputElement(foreground) ?? focused);
    }

    public static InputTarget CreateLineTarget(nint window) =>
        new(window, FindLineInputElement(window));

    public static nint FindLineWindow()
    {
        nint found = 0;
        EnumWindows((window, _) =>
        {
            if (!IsWindowVisible(window) || GetWindowTextLength(window) == 0)
            {
                return true;
            }

            if (IsLineWindow(window))
            {
                found = window;
                return false;
            }

            return true;
        }, 0);
        return found;
    }

    private static bool IsLineWindow(nint window)
    {
        GetWindowThreadProcessId(window, out var processId);
        try
        {
            using var process = Process.GetProcessById((int)processId);
            var title = GetWindowTitle(window);
            return process.ProcessName.Equals("LINE", StringComparison.OrdinalIgnoreCase) ||
                   title.Equals("LINE", StringComparison.OrdinalIgnoreCase) ||
                   title.EndsWith(" - LINE", StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return false;
        }
    }

    private static AutomationElement? FindLineInputElement(nint window)
    {
        try
        {
            var root = AutomationElement.FromHandle(window);
            var edits = root.FindAll(
                TreeScope.Descendants,
                new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Edit));

            AutomationElement? lowest = null;
            var lowestBottom = double.MinValue;
            for (var index = 0; index < edits.Count; index++)
            {
                var element = edits[index];
                var current = element.Current;
                var bounds = current.BoundingRectangle;
                if (!current.IsEnabled || current.IsOffscreen || bounds.IsEmpty ||
                    bounds.Width < 40 || bounds.Height < 12 || bounds.Bottom <= lowestBottom)
                {
                    continue;
                }

                lowest = element;
                lowestBottom = bounds.Bottom;
            }

            return lowest;
        }
        catch
        {
            return null;
        }
    }

    public static string GetWindowTitle(nint window)
    {
        var length = GetWindowTextLength(window);
        if (length <= 0)
        {
            return "";
        }

        var builder = new StringBuilder(length + 1);
        GetWindowText(window, builder, builder.Capacity);
        return builder.ToString();
    }

    public static void PositionNear(Window window, nint target)
    {
        if (target == 0 || !GetWindowRect(target, out var rect))
        {
            return;
        }

        var dpi = VisualTreeHelper.GetDpi(window);
        var workArea = SystemParameters.WorkArea;
        var targetLeft = rect.Left / dpi.DpiScaleX;
        var targetRight = rect.Right / dpi.DpiScaleX;
        var targetTop = rect.Top / dpi.DpiScaleY;

        var preferredLeft = targetRight + 10;
        window.Left = preferredLeft + window.Width <= workArea.Right
            ? preferredLeft
            : Math.Max(workArea.Left, targetLeft - window.Width - 10);
        window.Top = Math.Clamp(targetTop, workArea.Top, Math.Max(workArea.Top, workArea.Bottom - window.Height));
    }

    public static async Task<bool> InsertTextAsync(InputTarget target, string text)
    {
        if (!IsWindow(target.WindowHandle) || string.IsNullOrEmpty(text))
        {
            return false;
        }

        var input = FindLineInputElement(target.WindowHandle) ?? target.FocusedElement;
        SetForegroundWindow(target.WindowHandle);
        await Task.Delay(120);
        TryFocus(input);
        await Task.Delay(80);
        if (GetForegroundWindow() != target.WindowHandle)
        {
            return false;
        }

        if (TrySetEmptyAutomationValue(input, text))
        {
            return true;
        }

        IDataObject? previousClipboard = null;
        try
        {
            previousClipboard = Clipboard.GetDataObject();
        }
        catch
        {
            // Clipboard may temporarily be locked by another process.
        }

        try
        {
            Clipboard.SetText(text, TextDataFormat.UnicodeText);
            var inserted = SendControlV();
            await Task.Delay(350);
            return inserted;
        }
        catch
        {
            return false;
        }
        finally
        {
            try
            {
                if (previousClipboard is not null &&
                    Clipboard.ContainsText(TextDataFormat.UnicodeText) &&
                    Clipboard.GetText(TextDataFormat.UnicodeText) == text)
                {
                    Clipboard.SetDataObject(previousClipboard, true);
                }
            }
            catch
            {
                // Clipboard restoration must not turn a successful paste into a reported failure.
            }
        }
    }

    private static bool TryFocus(AutomationElement? element)
    {
        try
        {
            element?.SetFocus();
            return element is not null;
        }
        catch
        {
            return false;
        }
    }

    private static bool TrySetEmptyAutomationValue(AutomationElement? element, string text)
    {
        try
        {
            if (element is null ||
                !element.TryGetCurrentPattern(ValuePattern.Pattern, out var pattern) ||
                pattern is not ValuePattern valuePattern ||
                !string.IsNullOrEmpty(valuePattern.Current.Value))
            {
                return false;
            }

            valuePattern.SetValue(text);
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static bool SendControlV()
    {
        var inputs = new[]
        {
            KeyInput(VkControl, 0),
            KeyInput(VkV, 0),
            KeyInput(VkV, KeyUp),
            KeyInput(VkControl, KeyUp)
        };
        return SendInput((uint)inputs.Length, inputs, NativeInputSize) == inputs.Length;
    }

    private static Input KeyInput(ushort key, uint flags) => new()
    {
        Type = InputKeyboard,
        Union = new InputUnion
        {
            Keyboard = new KeyboardInput
            {
                VirtualKey = key,
                Flags = flags
            }
        }
    };
}
