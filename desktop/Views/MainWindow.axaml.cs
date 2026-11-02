using System.IO;
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
    private readonly RemoteServer _server = new();
    private readonly VideoFrameDecoder _decoder = new();
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
        VersionText.Text = $"Veyqo v{VersionInfo.Version} (Official Build)";
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

        /* Generate pairing QR code for Mobile to connect back to Desktop */
        string payload = NetworkDiscovery.BuildConnectionPayload(_localIp, _serverPort, Guid.NewGuid().ToString("N"));
        using var qrGenerator = new QRCodeGenerator();
        using var qrData = qrGenerator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.Q);
        using var qrCode = new PngByteQRCode(qrData);
        byte[] qrBytes = qrCode.GetGraphic(20);

        using var ms = new MemoryStream(qrBytes);
        QrCodeImage.Source = new Bitmap(ms);

        /* Start background server to accept incoming mobile connections */
        try
        {
            _server.Start(_serverPort);
            _server.OnClientConnected += HandleServerClientConnected;
            _server.OnFrameReceived += HandleFrameReceived;
            _server.OnDisconnected += HandleDisconnected;
        }
        catch
        {
        }
    }

    private void HookEvents()
    {
        ConnectButton.Click += async (_, _) => await HandleConnectAsync();
        DisconnectButton.Click += async (_, _) => await HandleDisconnectAsync();

        GithubButton.Click += (_, _) =>
        {
            try
            {
                System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "https://github.com/SyntX34/Veyqo",
                    UseShellExecute = true
                });
            }
            catch
            {
            }
        };

        AboutButton.Click += (_, _) =>
        {
            StatusText.Text = $"Veyqo v{VersionInfo.Version} by Samir Neupane (SyntX34)";
        };

        ThemeModeButton.Click += (_, _) =>
        {
            var app = Application.Current;
            if (app != null)
            {
                app.RequestedThemeVariant = app.ActualThemeVariant == ThemeVariant.Dark
                    ? ThemeVariant.Light
                    : ThemeVariant.Dark;
            }
            UpdateThemeVisuals();
        };

        _client.OnFrameReceived += HandleFrameReceived;
        _client.OnDisconnected += HandleDisconnected;

        RemoteScreenImage.PointerPressed += HandlePointerPressed;
        RemoteScreenImage.PointerMoved += HandlePointerMoved;
        RemoteScreenImage.PointerReleased += HandlePointerReleased;
    }

    private void HandleServerClientConnected(string remoteEndpoint)
    {
        Dispatcher.UIThread.Post(() =>
        {
            StatusText.Text = $"Status: Connected ({remoteEndpoint})";
            ConnectButton.IsVisible = false;
            DisconnectButton.IsVisible = true;
            StandbyPanel.IsVisible = false;
            ViewportBorder.IsVisible = true;
        });
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
            var dialog = new ErrorDialogWindow(
                $"Could not establish connection to {host}:{port}.\n\n" +
                $"Reason: {ex.Message}\n\n" +
                "Make sure both devices are on the same Wi-Fi network and Veyqo is running on your mobile device.");
            await dialog.ShowDialog(this);
        }
    }

    private async Task HandleDisconnectAsync()
    {
        await _client.DisposeAsync();
        await _server.DisposeAsync();
        HandleDisconnected();
    }

    private void HandleDisconnected()
    {
        Dispatcher.UIThread.Post(() =>
        {
            StatusText.Text = "Status: Disconnected / Standby";
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
            var bitmap = _decoder.DecodeFrame(frameData);
            if (bitmap != null)
            {
                RemoteScreenImage.Source = bitmap;
                if (StandbyPanel.IsVisible)
                {
                    StandbyPanel.IsVisible = false;
                    ViewportBorder.IsVisible = true;
                }
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

        var packet = new ControlPacket
        {
            Type = ControlMessageType.TouchDown,
            X = normX,
            Y = normY
        };

        _ = _client.SendPacketAsync(packet);
        _ = _server.SendPacketAsync(packet);
    }

    private void HandlePointerMoved(object? sender, PointerEventArgs e)
    {
        if (!e.GetCurrentPoint(RemoteScreenImage).Properties.IsLeftButtonPressed) return;

        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        var packet = new ControlPacket
        {
            Type = ControlMessageType.TouchMove,
            X = normX,
            Y = normY
        };

        _ = _client.SendPacketAsync(packet);
        _ = _server.SendPacketAsync(packet);
    }

    private void HandlePointerReleased(object? sender, PointerReleasedEventArgs e)
    {
        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        var packet = new ControlPacket
        {
            Type = ControlMessageType.TouchUp,
            X = normX,
            Y = normY
        };

        _ = _client.SendPacketAsync(packet);
        _ = _server.SendPacketAsync(packet);
    }
}
