using System.Windows;

namespace ToneIME;

public partial class App : System.Windows.Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        if (e.Args.Contains("--self-test", StringComparer.OrdinalIgnoreCase))
        {
            try
            {
                SelfTest.Run();
                Console.WriteLine("ToneIME self-test passed.");
                Shutdown(0);
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"ToneIME self-test failed: {ex.Message}");
                Shutdown(1);
            }

            return;
        }

        base.OnStartup(e);
    }
}
