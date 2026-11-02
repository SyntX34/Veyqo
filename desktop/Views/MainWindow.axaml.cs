using System.IO;
using System.Reflection;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Styling;
using Avalonia.Threading;
using QRCoder;
using Veyqo.Desktop.Network;

namespace Veyqo.Desktop.Views;

public partial class MainWindow : Window
{
    private readonly RemoteClient _client = new();
    private string _localIp = "127.0.0.1";
    private readonly int _serverPort = 55443;
    private bool _isFollowSystemTheme = true;

    public MainWindow()
    {
        InitializeComponent();
        InitializeVersionInfo();
        InitializeTheme();
        InitializeNetwork();
        HookEvents();
    }

    private void InitializeVersionInfo()
    {
        var version = Assembly.GetExecutingAssembly().GetName().Version;
        string verString = version != null ? $"{version.Major}.{version.Minor}.{version.Build}" : "1.0.0";
        VersionText.Text = $"Veyqo v{verString} (Official Build)";
    }

    private void InitializeTheme()
    {
        UpdateThemeVisuals();
        ActualThemeVariantChanged += (_, _) =>
        {
            if (_isFollowSystemTheme)
            {
                UpdateThemeVisuals();
            }
        };
    }

    private void UpdateThemeVisuals()
    {
        bool isDark = ActualThemeVariant == ThemeVariant.Dark;

        /* Apply dynamic palette adjustments */
        if (isDark)
        {
            Background = new SolidColorBrush(Color.Parse("#0b0f19"));
            SidebarBorder.Background = new SolidColorBrush(Color.Parse("#111827"));
            SidebarBorder.BorderBrush = new SolidColorBrush(Color.Parse("#1f2937"));
            VersionBannerBorder.Background = new SolidColorBrush(Color.Parse("#1e293b"));
            StatusCardBorder.Background = new SolidColorBrush(Color.Parse("#1f2937"));
            QrCardBorder.Background = new SolidColorBrush(Color.Parse("#1e293b"));
            DirectConnectBorder.Background = new SolidColorBrush(Color.Parse("#1f2937"));
            ViewportBackgroundBorder.Background = new SolidColorBrush(Color.Parse("#030712"));
            ThemeModeButton.Content = "☀️";
        }
        else
        {
            Background = new SolidColorBrush(Color.Parse("#f8fafc"));
            SidebarBorder.Background = new SolidColorBrush(Color.Parse("#ffffff"));
            SidebarBorder.BorderBrush = new SolidColorBrush(Color.Parse("#e2e8f0"));
            VersionBannerBorder.Background = new SolidColorBrush(Color.Parse("#f1f5f9"));
            StatusCardBorder.Background = new SolidColorBrush(Color.Parse("#f8fafc"));
            QrCardBorder.Background = new SolidColorBrush(Color.Parse("#f1f5f9"));
            DirectConnectBorder.Background = new SolidColorBrush(Color.Parse("#f8fafc"));
            ViewportBackgroundBorder.Background = new SolidColorBrush(Color.Parse("#f1f5f9"));
            ThemeModeButton.Content = "🌙";
        }

        string modeName = isDark ? "Dark" : "Light";
        ThemeLabelText.Text = _isFollowSystemTheme ? $"{modeName} (OS)" : modeName;
    }

    private void InitializeNetwork()
    {
        _localIp = NetworkDiscovery.GetLocalIpAddress();
        LocalIpText.Text = $"Host: {_localIp}:{_serverPort}";

        /* Generate pairing QR code */
        string payload = NetworkDiscovery.BuildConnectionPayload(_localIp, _serverPort, Guid.NewGuid().ToString("N"));
        using var qrGenerator = new QRCodeGenerator();
        using var qrData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.Q);
        using var qrCode = new PngByteQRCode(qrData);
        byte[] qrBytes = qrCode.GetGraphic(20);

        using var ms = new MemoryStream(qrBytes);
        QrCodeImage.Source = new Bitmap(ms);
    }

    private void HookEvents()
    {
        ConnectButton.Click += async (_, _) => await HandleConnectAsync();
        DisconnectButton.Click += async (_, _) => await HandleDisconnectAsync();

        ThemeModeButton.Click += (_, _) =>
        {
            _isFollowSystemTheme = false;
            var app = Application.Current;
            if (app != null)
            {
                app.RequestedThemeVariant = app.ActualThemeVariant == ThemeVariant.Dark
                    ? ThemeVariant.Light
                    : ThemeVariant.Dark;
            }
            UpdateThemeVisuals();
        };

        ThemeAutoButton.Click += (_, _) =>
        {
            _isFollowSystemTheme = true;
            var app = Application.Current;
            if (app != null)
            {
                app.RequestedThemeVariant = ThemeVariant.Default;
            }
            UpdateThemeVisuals();
        };

        _client.OnFrameReceived += HandleFrameReceived;
        _client.OnDisconnected += HandleDisconnected;

        RemoteScreenImage.PointerPressed += HandlePointerPressed;
        RemoteScreenImage.PointerMoved += HandlePointerMoved;
        RemoteScreenImage.PointerReleased += HandlePointerReleased;
    }

    private async Task HandleConnectAsync()
    {
        string host = TargetHostBox.Text?.Trim() ?? string.Empty;
        if (string.IsNullOrWhiteSpace(host))
        {
            StatusText.Text = "Status: Enter valid mobile IP";
            return;
        }

        if (!int.TryParse(TargetPortBox.Text, out int port))
        {
            port = _serverPort;
        }

        StatusText.Text = $"Connecting to {host}:{port}...";
        try
        {
            await _client.ConnectAsync(host, port);
            StatusText.Text = "Status: Connected";
            ConnectButton.IsVisible = false;
            DisconnectButton.IsVisible = true;
            StandbyPanel.IsVisible = false;
            ViewportBorder.IsVisible = true;
        }
        catch (Exception ex)
        {
            StatusText.Text = $"Status: Error ({ex.Message})";
        }
    }

    private async Task HandleDisconnectAsync()
    {
        await _client.DisposeAsync();
        HandleDisconnected();
    }

    private void HandleDisconnected()
    {
        Dispatcher.UIThread.Post(() =>
        {
            StatusText.Text = "Status: Disconnected";
            ConnectButton.IsVisible = true;
            DisconnectButton.IsVisible = false;
            StandbyPanel.IsVisible = true;
            ViewportBorder.IsVisible = false;
        });
    }

    private void HandleFrameReceived(byte[] frameData)
    {
        Dispatcher.UIThread.Post(() =>
        {
            try
            {
                using var ms = new MemoryStream(frameData);
                RemoteScreenImage.Source = new Bitmap(ms);
            }
            catch
            {
                /* Ignore malformed frames */
            }
        });
    }

    private void HandlePointerPressed(object? sender, PointerPressedEventArgs e)
    {
        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        _ = _client.SendPacketAsync(new ControlPacket
        {
            Type = ControlMessageType.TouchDown,
            X = normX,
            Y = normY
        });
    }

    private void HandlePointerMoved(object? sender, PointerEventArgs e)
    {
        if (!e.GetCurrentPoint(RemoteScreenImage).Properties.IsLeftButtonPressed) return;

        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        _ = _client.SendPacketAsync(new ControlPacket
        {
            Type = ControlMessageType.TouchMove,
            X = normX,
            Y = normY
        });
    }

    private void HandlePointerReleased(object? sender, PointerReleasedEventArgs e)
    {
        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        _ = _client.SendPacketAsync(new ControlPacket
        {
            Type = ControlMessageType.TouchUp,
            X = normX,
            Y = normY
        });
    }
}
