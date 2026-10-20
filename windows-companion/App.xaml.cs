using System.Windows;

namespace TouchDeckCompanion;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        // Set shutdown mode to explicit - we control when the app exits
        ShutdownMode = ShutdownMode.OnMainWindowClose;
    }
}
