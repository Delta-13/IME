using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace ToneIME;

public partial class RegionSelectorWindow : Window
{
    private Point _start;
    private bool _dragging;

    public ScreenRegion? SelectedRegion { get; private set; }

    public RegionSelectorWindow()
    {
        InitializeComponent();
        Left = SystemParameters.VirtualScreenLeft;
        Top = SystemParameters.VirtualScreenTop;
        Width = SystemParameters.VirtualScreenWidth;
        Height = SystemParameters.VirtualScreenHeight;
    }

    private void OnMouseDown(object sender, MouseButtonEventArgs e)
    {
        _start = e.GetPosition(this);
        _dragging = true;
        CaptureMouse();
        SelectionBorder.Visibility = Visibility.Visible;
        UpdateSelection(_start);
    }

    private void OnMouseMove(object sender, MouseEventArgs e)
    {
        if (_dragging)
        {
            UpdateSelection(e.GetPosition(this));
        }
    }

    private void OnMouseUp(object sender, MouseButtonEventArgs e)
    {
        if (!_dragging)
        {
            return;
        }

        var end = e.GetPosition(this);
        _dragging = false;
        ReleaseMouseCapture();

        var startScreen = PointToScreen(_start);
        var endScreen = PointToScreen(end);
        var region = new ScreenRegion(
            (int)Math.Round(Math.Min(startScreen.X, endScreen.X)),
            (int)Math.Round(Math.Min(startScreen.Y, endScreen.Y)),
            (int)Math.Round(Math.Abs(endScreen.X - startScreen.X)),
            (int)Math.Round(Math.Abs(endScreen.Y - startScreen.Y)));
        if (!region.IsValid)
        {
            return;
        }

        SelectedRegion = region;
        DialogResult = true;
    }

    private void UpdateSelection(Point current)
    {
        var left = Math.Min(_start.X, current.X);
        var top = Math.Min(_start.Y, current.Y);
        SelectionBorder.Width = Math.Abs(current.X - _start.X);
        SelectionBorder.Height = Math.Abs(current.Y - _start.Y);
        Canvas.SetLeft(SelectionBorder, left);
        Canvas.SetTop(SelectionBorder, top);
    }

    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            DialogResult = false;
        }
    }
}
