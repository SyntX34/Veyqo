using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace Veyqo.Desktop.Network;

/* TCP Server on desktop to accept incoming connections when mobile scans QR code */
public sealed class RemoteServer : IAsyncDisposable
{
    private TcpListener? _listener;
    private TcpClient? _activeClient;
    private NetworkStream? _stream;
    private CancellationTokenSource? _cts;

    public bool IsClientConnected => _activeClient?.Connected ?? false;

    public event Action<string>? OnClientConnected;
    public event Action<byte[]>? OnFrameReceived;
    public event Action? OnDisconnected;

    public void Start(int port)
    {
        _cts = new CancellationTokenSource();
        _listener = new TcpListener(IPAddress.Any, port);
        _listener.Server.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _listener.Start();

        _ = Task.Run(() => AcceptLoopAsync(_cts.Token));
    }

    private async Task AcceptLoopAsync(CancellationToken token)
    {
        while (!token.IsCancellationRequested && _listener != null)
        {
            try
            {
                var client = await _listener.AcceptTcpClientAsync(token);
                _activeClient?.Dispose();
                _activeClient = client;
                _stream = client.GetStream();

                string clientEndpoint = client.Client.RemoteEndPoint?.ToString() ?? "Unknown";
                OnClientConnected?.Invoke(clientEndpoint);

                _ = Task.Run(() => ReadLoopAsync(_stream, token), token);
            }
            catch
            {
                break;
            }
        }
    }

    private async Task ReadLoopAsync(NetworkStream stream, CancellationToken token)
    {
        byte[] lengthBuffer = new byte[4];

        try
        {
            while (!token.IsCancellationRequested && stream != null)
            {
                int readBytes = await ReadExactAsync(stream, lengthBuffer, 0, 4, token);
                if (readBytes == 0) break;

                int packetLength = BitConverter.ToInt32(lengthBuffer, 0);
                if (packetLength <= 0 || packetLength > 10 * 1024 * 1024) break;

                byte[] buffer = new byte[packetLength];
                int totalRead = await ReadExactAsync(stream, buffer, 0, packetLength, token);
                if (totalRead < packetLength) break;

                OnFrameReceived?.Invoke(buffer);
            }
        }
        catch
        {
        }
        finally
        {
            OnDisconnected?.Invoke();
        }
    }

    private static async Task<int> ReadExactAsync(NetworkStream stream, byte[] buffer, int offset, int count, CancellationToken token)
    {
        int totalRead = 0;
        while (totalRead < count)
        {
            int read = await stream.ReadAsync(buffer.AsMemory(offset + totalRead, count - totalRead), token);
            if (read == 0) return totalRead;
            totalRead += read;
        }
        return totalRead;
    }

    public async Task SendPacketAsync(ControlPacket packet)
    {
        if (_stream == null || !IsClientConnected) return;

        string json = JsonSerializer.Serialize(packet);
        byte[] payloadBytes = Encoding.UTF8.GetBytes(json);
        byte[] headerBytes = BitConverter.GetBytes(payloadBytes.Length);

        await _stream.WriteAsync(headerBytes, 0, headerBytes.Length);
        await _stream.WriteAsync(payloadBytes, 0, payloadBytes.Length);
        await _stream.FlushAsync();
    }

    public async ValueTask DisposeAsync()
    {
        _cts?.Cancel();
        _listener?.Stop();
        _stream?.Dispose();
        _activeClient?.Dispose();
        await Task.CompletedTask;
    }
}
