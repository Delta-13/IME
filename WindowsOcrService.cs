using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Globalization;
using Windows.Graphics.Imaging;
using Windows.Media.Ocr;

namespace ToneIME;

internal sealed record OcrCaptureResult(string Text, ulong ImageHash, byte[] PngBytes);

internal sealed class WindowsOcrService
{
    private readonly OcrEngine? _engine =
        OcrEngine.TryCreateFromLanguage(new Language("ja-JP")) ??
        OcrEngine.TryCreateFromUserProfileLanguages();

    public bool IsAvailable => _engine is not null;

    public async Task<OcrCaptureResult> CaptureAsync(ScreenRegion region, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        using var bitmap = new Bitmap(region.Width, region.Height, PixelFormat.Format32bppArgb);
        using (var graphics = Graphics.FromImage(bitmap))
        {
            graphics.CopyFromScreen(region.X, region.Y, 0, 0, bitmap.Size, CopyPixelOperation.SourceCopy);
        }

        var rectangle = new Rectangle(0, 0, bitmap.Width, bitmap.Height);
        var data = bitmap.LockBits(rectangle, ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        byte[] pixels;
        try
        {
            var stride = Math.Abs(data.Stride);
            pixels = new byte[stride * data.Height];
            Marshal.Copy(data.Scan0, pixels, 0, pixels.Length);
        }
        finally
        {
            bitmap.UnlockBits(data);
        }

        var hash = SampledHash(pixels);
        using var png = new MemoryStream();
        bitmap.Save(png, ImageFormat.Png);
        if (_engine is null)
        {
            return new OcrCaptureResult("", hash, png.ToArray());
        }

        using var softwareBitmap = SoftwareBitmap.CreateCopyFromBuffer(
            pixels.AsBuffer(),
            BitmapPixelFormat.Bgra8,
            bitmap.Width,
            bitmap.Height,
            BitmapAlphaMode.Premultiplied);
        var result = await _engine.RecognizeAsync(softwareBitmap);
        cancellationToken.ThrowIfCancellationRequested();
        return new OcrCaptureResult(result.Text?.Trim() ?? "", hash, png.ToArray());
    }

    private static ulong SampledHash(byte[] pixels)
    {
        const ulong offset = 14695981039346656037;
        const ulong prime = 1099511628211;
        var hash = offset;
        var step = Math.Max(4, pixels.Length / 4096);
        for (var index = 0; index < pixels.Length; index += step)
        {
            hash ^= pixels[index];
            hash *= prime;
        }

        return hash;
    }
}
