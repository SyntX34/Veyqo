using System.IO;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Media.Imaging;
using Avalonia.Threading;
using QRCoder;
using Veyqo.Desktop.Network;

namespace Veyqo.Desktop.Views;

public partial class MainWindow : Window
{
    private readonly RemoteClient _client = new();
    private string _localIp = "127.0.0.1";
    private readonly int _serverPort = 55443;

    public MainWindow()
    {
        InitializeComponent();
        InitializeNetwork();
        HookEvents();
    }

    private void InitializeNetwork()
    {
        _localIp = NetworkDiscovery.GetLocalIpAddress();
        LocalIpText.Text = $"Host: {_localIp}:{_serverPort}";

        /* Generate QR Code for Mobile Scanning */
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
                /* Drop corrupted frame gracefully */
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
