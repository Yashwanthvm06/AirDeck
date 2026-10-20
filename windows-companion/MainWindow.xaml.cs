using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Shapes;
using TouchDeckCompanion.Models;
using TouchDeckCompanion.Services;

namespace TouchDeckCompanion;

public partial class MainWindow : Window
{
    private readonly InputSimulator _inputSimulator;
    private readonly SecurityManager _securityManager;
    private readonly WebSocketServer _server;
    private bool _minimizeToTray;
    private bool _isExiting;

    public MainWindow()
    {
        InitializeComponent();

        _inputSimulator = new InputSimulator();
        _securityManager = new SecurityManager();
        _server = new WebSocketServer(_inputSimulator, _securityManager);

        // Wire up events
        _server.OnLog += msg => Dispatcher.Invoke(() => AddLog(msg));
        _server.OnServerStateChanged += running => Dispatcher.Invoke(() => UpdateServerState(running));
        _server.OnClientConnected += (id, name) => Dispatcher.Invoke(() => OnDeviceConnected(id, name));
        _server.OnClientDisconnected += id => Dispatcher.Invoke(() => OnDeviceDisconnected(id));
        _server.OnPairingCodeGenerated += code => Dispatcher.Invoke(() => ShowPairingCode(code));

        _server.GamepadService.OnGamepadStatusChanged += (active, msg) => Dispatcher.Invoke(() => UpdateGamepadUI(active, msg));
        UpdateGamepadUI(_server.GamepadService.IsViGEmActive, _server.GamepadService.StatusMessage);

        // Set connection info
        ConnInfoIp.Text = WebSocketServer.GetLocalIPAddress();
        ConnInfoPort.Text = _server.Port.ToString();
        ConnInfoName.Text = Environment.MachineName;

        RefreshPairedDevices();

        // Auto-start server on launch
        Loaded += (_, _) => StartServer_Click(this, new RoutedEventArgs());
    }

    private void UpdateGamepadUI(bool isViGEmActive, string statusMessage)
    {
        if (isViGEmActive)
        {
            GamepadBadge.Background = new SolidColorBrush(Color.FromRgb(0x1B, 0x3A, 0x22));
            GamepadBadgeText.Text = "Xbox 360 Active";
            GamepadBadgeText.Foreground = (Brush)FindResource("SuccessGreenBrush");
            GamepadDescText.Text = "Native Xbox 360 Controller emulation is ACTIVE. Games (Steam, GamePass, Emulators) recognize your phone as a hardware Xbox 360 controller.";
            InstallViGEmButton.Visibility = Visibility.Collapsed;
        }
        else
        {
            GamepadBadge.Background = new SolidColorBrush(Color.FromRgb(0x3D, 0x2E, 0x12));
            GamepadBadgeText.Text = "WASD Gaming Mode";
            GamepadBadgeText.Foreground = (Brush)FindResource("WarningAmberBrush");
            GamepadDescText.Text = "Smart PC gaming mode (WASD movement, mouse aim, space jump). For games to detect your phone as a real Xbox 360 controller with zero setup, install the free ViGEmBus driver.";
            InstallViGEmButton.Visibility = Visibility.Visible;
        }
    }

    private void InstallViGEm_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
            {
                FileName = "https://github.com/nefarius/ViGEmBus/releases/latest",
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            AddLog($"Could not open browser: {ex.Message}");
        }
    }

    private async void StartServer_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            StartButton.IsEnabled = false;
            await _server.StartAsync();
        }
        catch (Exception ex)
        {
            AddLog($"Failed to start server: {ex.Message}");
            StartButton.IsEnabled = true;
            MessageBox.Show(
                $"Failed to start TouchDeck server:\n\n{ex.Message}\n\n" +
                "Try running as Administrator, or check if port 9876 is already in use.",
                "TouchDeck - Error",
                MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private async void StopServer_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            await _server.StopAsync();
        }
        catch (Exception ex)
        {
            AddLog($"Error stopping server: {ex.Message}");
        }
    }

    private void UpdateServerState(bool running)
    {
        if (running)
        {
            StatusDot.Fill = (Brush)FindResource("SuccessGreenBrush");
            StatusText.Text = "Server Running";
            StatusText.Foreground = (Brush)FindResource("SuccessGreenBrush");
            IpAddressText.Text = $"ws://{WebSocketServer.GetLocalIPAddress()}:{_server.Port}";
            ConnInfoIp.Text = WebSocketServer.GetLocalIPAddress();
            StartButton.IsEnabled = false;
            StopButton.IsEnabled = true;
        }
        else
        {
            StatusDot.Fill = (Brush)FindResource("ErrorRedBrush");
            StatusText.Text = "Server Stopped";
            StatusText.Foreground = (Brush)FindResource("TextSecondaryBrush");
            IpAddressText.Text = "";
            StartButton.IsEnabled = true;
            StopButton.IsEnabled = false;
        }
    }

    private void OnDeviceConnected(string deviceId, string deviceName)
    {
        NoDevicesText.Visibility = Visibility.Collapsed;

        var border = new Border
        {
            Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x1E, 0x2A)),
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(12, 8, 12, 8),
            Margin = new Thickness(0, 4, 0, 4),
            Tag = deviceId
        };

        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var nameStack = new StackPanel();
        nameStack.Children.Add(new TextBlock
        {
            Text = deviceName,
            FontSize = 13,
            FontWeight = FontWeights.SemiBold,
            Foreground = (Brush)FindResource("TextPrimaryBrush")
        });
        nameStack.Children.Add(new TextBlock
        {
            Text = "Connected",
            FontSize = 11,
            Foreground = (Brush)FindResource("SuccessGreenBrush")
        });

        var dot = new Ellipse
        {
            Width = 8, Height = 8,
            Fill = (Brush)FindResource("SuccessGreenBrush"),
            VerticalAlignment = VerticalAlignment.Center
        };

        Grid.SetColumn(nameStack, 0);
        Grid.SetColumn(dot, 1);
        grid.Children.Add(nameStack);
        grid.Children.Add(dot);

        border.Child = grid;
        DevicesList.Children.Add(border);

        DeviceCountText.Text = DevicesList.Children.Count.ToString();

        // Hide pairing card after successful connection
        PairingCard.Visibility = Visibility.Collapsed;
    }

    private void OnDeviceDisconnected(string deviceId)
    {
        var toRemove = DevicesList.Children.OfType<Border>()
            .FirstOrDefault(b => b.Tag?.ToString() == deviceId);

        if (toRemove != null)
        {
            DevicesList.Children.Remove(toRemove);
        }

        DeviceCountText.Text = DevicesList.Children.Count.ToString();
        if (DevicesList.Children.Count == 0)
        {
            NoDevicesText.Visibility = Visibility.Visible;
        }
    }

    private void ShowPairingCode(string code)
    {
        PairingCodeText.Text = code;
        PairingCard.Visibility = Visibility.Visible;
    }

    private void RefreshPairedDevices()
    {
        PairedDevicesList.Children.Clear();
        var devices = _securityManager.GetPairedDevices();

        if (devices.Count == 0)
        {
            NoPairedText.Visibility = Visibility.Visible;
            return;
        }

        NoPairedText.Visibility = Visibility.Collapsed;

        foreach (var device in devices)
        {
            var border = new Border
            {
                Background = new SolidColorBrush(Color.FromRgb(0x1E, 0x1E, 0x2A)),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(12, 8, 12, 8),
                Margin = new Thickness(0, 4, 0, 4)
            };

            var grid = new Grid();
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            var nameStack = new StackPanel();
            nameStack.Children.Add(new TextBlock
            {
                Text = device.DeviceName,
                FontSize = 13,
                Foreground = (Brush)FindResource("TextPrimaryBrush")
            });
            nameStack.Children.Add(new TextBlock
            {
                Text = $"Paired {device.PairedAt:MMM dd, yyyy}",
                FontSize = 11,
                Foreground = (Brush)FindResource("TextSecondaryBrush")
            });

            var revokeBtn = new Button
            {
                Content = "Revoke",
                Style = (Style)FindResource("TouchDeckButton"),
                Padding = new Thickness(10, 4, 10, 4),
                FontSize = 11,
                Tag = device.DeviceId,
                VerticalAlignment = VerticalAlignment.Center
            };
            revokeBtn.Click += (s, e) =>
            {
                var did = ((Button)s).Tag?.ToString();
                if (did != null)
                {
                    var confirm = MessageBox.Show(
                        $"Revoke device \"{device.DeviceName}\"?\n\nThis will immediately disconnect the device if it's currently connected.",
                        "TouchDeck - Revoke Device",
                        MessageBoxButton.YesNo, MessageBoxImage.Warning);

                    if (confirm == MessageBoxResult.Yes)
                    {
                        _securityManager.RevokeDevice(did);
                        // Force-disconnect is handled automatically via OnDeviceRevoked event
                        RefreshPairedDevices();
                        AddLog($"Revoked device: {device.DeviceName}");
                    }
                }
            };

            Grid.SetColumn(nameStack, 0);
            Grid.SetColumn(revokeBtn, 1);
            grid.Children.Add(nameStack);
            grid.Children.Add(revokeBtn);

            border.Child = grid;
            PairedDevicesList.Children.Add(border);
        }
    }

    private void AddLog(string message)
    {
        LogList.Items.Add(message);
        if (LogList.Items.Count > 200)
        {
            LogList.Items.RemoveAt(0);
        }
        LogList.ScrollIntoView(LogList.Items[^1]);
    }

    private void ClearLog_Click(object sender, RoutedEventArgs e)
    {
        LogList.Items.Clear();
    }

    private void MinimizeToTray_Click(object sender, RoutedEventArgs e)
    {
        _minimizeToTray = true;
        WindowState = WindowState.Minimized;
    }

    private void Window_StateChanged(object sender, EventArgs e)
    {
        if (WindowState == WindowState.Minimized && _minimizeToTray)
        {
            Hide();
            _minimizeToTray = false;
        }
    }

    private async void Window_Closing(object sender, CancelEventArgs e)
    {
        if (!_isExiting && _server.IsRunning)
        {
            e.Cancel = true;
            var result = MessageBox.Show(
                "TouchDeck server is still running. Do you want to stop the server and exit?",
                "TouchDeck", MessageBoxButton.YesNo, MessageBoxImage.Question);

            if (result == MessageBoxResult.Yes)
            {
                _isExiting = true;
                await _server.StopAsync();
                _server.Dispose();
                Close();
            }
        }
        else
        {
            _server.Dispose();
        }
    }
}