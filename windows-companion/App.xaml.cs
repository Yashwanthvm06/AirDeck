using System.IO;
using System.Windows;

namespace TouchDeckCompanion;

public partial class App : Application
{
    private static readonly string LogPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "airdesk_debug.log");

    public static void Log(string msg)
    {
        try
        {
            File.AppendAllText(LogPath, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] {msg}{Environment.NewLine}");
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        Log("=== AirDesk App OnStartup begin ===");

        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            Log($"CRITICAL Unhandled AppDomain Exception: {args.ExceptionObject}");
        };

        DispatcherUnhandledException += (s, args) =>
        {
            Log($"CRITICAL Dispatcher Unhandled Exception: {args.Exception}");
        };

        base.OnStartup(e);

        ShutdownMode = ShutdownMode.OnMainWindowClose;
        Log("AirDesk App OnStartup completed");
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Log($"AirDesk App OnExit with code {e.ApplicationExitCode}");
        base.OnExit(e);
    }
}
