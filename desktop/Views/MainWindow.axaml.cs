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

    /* Pointer gesture tracking state */
    private float _pointerDownX;
    private float _pointerDownY;
    private long _pointerDownTime;
    private bool _isPointerActive;

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

        AboutButton.Click += async (_, _) =>
        {
            var dialog = new AboutDialogWindow();
            await dialog.ShowDialog(this);
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

        /* Pointer & Gesture events */
        RemoteScreenImage.PointerPressed += HandlePointerPressed;
        RemoteScreenImage.PointerMoved += HandlePointerMoved;
        RemoteScreenImage.PointerReleased += HandlePointerReleased;
        RemoteScreenImage.PointerWheelChanged += HandlePointerWheelChanged;

        /* Android Navigation Buttons */
        NavBackButton.Click += (_, _) => SendControlPacket(new ControlPacket { Type = ControlMessageType.NavBack });
        NavHomeButton.Click += (_, _) => SendControlPacket(new ControlPacket { Type = ControlMessageType.NavHome });
        NavRecentsButton.Click += (_, _) => SendControlPacket(new ControlPacket { Type = ControlMessageType.NavRecents });

        /* Keyboard input forwarding */
        KeyDown += HandleKeyDown;
        TextInput += HandleTextInput;

        /* Drag and Drop File Transfer */
        DragDrop.SetAllowDrop(ViewportBorder, true);
        ViewportBorder.AddHandler(DragDrop.DropEvent, HandleFileDrop);
    }

    private void HandleServerClientConnected(string remoteEndpoint)
    {
        Dispatcher.UIThread.Post(() =>
        {
            StatusText.Text = $"Status: Connected ({remoteEndpoint})";
            ConnectButton.IsVisible = false;
            DisconnectButton.IsVisible = true;
            RemoteScreenImage.Focus();
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
            RemoteScreenImage.Focus();
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

    private void SendControlPacket(ControlPacket packet)
    {
        _ = _client.SendPacketAsync(packet);
        _ = _server.SendPacketAsync(packet);
    }

    private void HandlePointerPressed(object? sender, PointerPressedEventArgs e)
    {
        RemoteScreenImage.Focus();
        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        _pointerDownX = (float)(point.Position.X / size.Width);
        _pointerDownY = (float)(point.Position.Y / size.Height);
        _pointerDownTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        _isPointerActive = true;

        SendControlPacket(new ControlPacket
        {
            Type = ControlMessageType.TouchDown,
            X = _pointerDownX,
            Y = _pointerDownY
        });
    }

    private void HandlePointerMoved(object? sender, PointerEventArgs e)
    {
        if (!_isPointerActive || !e.GetCurrentPoint(RemoteScreenImage).Properties.IsLeftButtonPressed) return;

        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);

        SendControlPacket(new ControlPacket
        {
            Type = ControlMessageType.TouchMove,
            X = normX,
            Y = normY
        });
    }

    private void HandlePointerReleased(object? sender, PointerReleasedEventArgs e)
    {
        if (!_isPointerActive) return;
        _isPointerActive = false;

        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float releaseX = (float)(point.Position.X / size.Width);
        float releaseY = (float)(point.Position.Y / size.Height);
        long elapsed = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - _pointerDownTime;

        float deltaX = releaseX - _pointerDownX;
        float deltaY = releaseY - _pointerDownY;
        float distanceSquared = deltaX * deltaX + deltaY * deltaY;

        /* If drag distance exceeds threshold, dispatch as a smooth gesture swipe */
        if (distanceSquared > 0.002f)
        {
            SendControlPacket(new ControlPacket
            {
                Type = ControlMessageType.Swipe,
                X = _pointerDownX,
                Y = _pointerDownY,
                EndX = releaseX,
                EndY = releaseY,
                DurationMs = Math.Clamp(elapsed, 150, 600)
            });
        }
        else
        {
            SendControlPacket(new ControlPacket
            {
                Type = ControlMessageType.TouchUp,
                X = releaseX,
                Y = releaseY
            });
        }
    }

    private void HandlePointerWheelChanged(object? sender, PointerWheelEventArgs e)
    {
        var point = e.GetCurrentPoint(RemoteScreenImage);
        var size = RemoteScreenImage.Bounds;
        if (size.Width <= 0 || size.Height <= 0) return;

        float normX = (float)(point.Position.X / size.Width);
        float normY = (float)(point.Position.Y / size.Height);
        float scrollDelta = (float)e.Delta.Y;

        SendControlPacket(new ControlPacket
        {
            Type = ControlMessageType.Scroll,
            X = normX,
            Y = scrollDelta
        });
    }

    private void HandleTextInput(object? sender, TextInputEventArgs e)
    {
        if (string.IsNullOrEmpty(e.Text)) return;
        if (!ViewportBorder.IsVisible) return;

        SendControlPacket(new ControlPacket
        {
            Type = ControlMessageType.TextInject,
            Payload = e.Text
        });
    }

    private void HandleKeyDown(object? sender, KeyEventArgs e)
    {
        if (!ViewportBorder.IsVisible) return;

        switch (e.Key)
        {
            case Key.Back:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.KeyEvent,
                    KeyCode = 67 /* Android KEYCODE_DEL */
                });
                e.Handled = true;
                break;

            case Key.Enter:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.KeyEvent,
                    KeyCode = 66 /* Android KEYCODE_ENTER */
                });
                e.Handled = true;
                break;

            case Key.Escape:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.NavBack
                });
                e.Handled = true;
                break;

            case Key.Left:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.Swipe,
                    X = 0.8f,
                    Y = 0.5f,
                    EndX = 0.2f,
                    EndY = 0.5f,
                    DurationMs = 250
                });
                e.Handled = true;
                break;

            case Key.Right:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.Swipe,
                    X = 0.2f,
                    Y = 0.5f,
                    EndX = 0.8f,
                    EndY = 0.5f,
                    DurationMs = 250
                });
                e.Handled = true;
                break;

            case Key.Up:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.Swipe,
                    X = 0.5f,
                    Y = 0.7f,
                    EndX = 0.5f,
                    EndY = 0.3f,
                    DurationMs = 250
                });
                e.Handled = true;
                break;

            case Key.Down:
                SendControlPacket(new ControlPacket
                {
                    Type = ControlMessageType.Swipe,
                    X = 0.5f,
                    Y = 0.3f,
                    EndX = 0.5f,
                    EndY = 0.7f,
                    DurationMs = 250
                });
                e.Handled = true;
                break;

            /* Hardware Keyboard Mapping & Shortcuts: Ctrl+V for clipboard sync to mobile */
            case Key.V when e.KeyModifiers.HasFlag(KeyModifiers.Control):
                _ = SyncClipboardToMobileAsync();
                e.Handled = true;
                break;
        }
    }

    private async Task SyncClipboardToMobileAsync()
    {
        try
        {
            var clipboard = TopLevel.GetTopLevel(this)?.Clipboard;
            if (clipboard != null)
            {
                string? text = await clipboard.GetTextAsync();
                if (!string.IsNullOrEmpty(text))
                {
                    SendControlPacket(new ControlPacket
                    {
                        Type = ControlMessageType.ClipboardSync,
                        Payload = text
                    });
                }
            }
        }
        catch
        {
        }
    }

    private async void HandleFileDrop(object? sender, DragEventArgs e)
    {
        var files = e.Data.GetFiles();
        if (files == null) return;

        foreach (var file in files)
        {
            try
            {
                string? path = file.Path.LocalPath;
                if (!string.IsNullOrEmpty(path) && File.Exists(path))
                {
                    FileInfo fi = new FileInfo(path);
                    /* Transfer files up to 25MB over TCP packet */
                    if (fi.Length <= 25 * 1024 * 1024)
                    {
                        byte[] fileBytes = await File.ReadAllBytesAsync(path);
                        string base64 = Convert.ToBase64String(fileBytes);
                        SendControlPacket(new ControlPacket
                        {
                            Type = ControlMessageType.FileChunk,
                            FileName = fi.Name,
                            FileData = base64
                        });
                        StatusText.Text = $"Status: Sent file {fi.Name}";
                    }
                }
            }
            catch
            {
            }
        }
    }
}
