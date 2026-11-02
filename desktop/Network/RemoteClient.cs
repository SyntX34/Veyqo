using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace Veyqo.Desktop.Network;

/* TCP client managing direct streaming and input control to mobile client */
public sealed class RemoteClient : IAsyncDisposable
{
    private TcpClient? _client;
    private NetworkStream? _stream;
    private CancellationTokenSource? _cts;

    public bool IsConnected => _client?.Connected ?? false;

    public event Action<byte[]>? OnFrameReceived;
    public event Action? OnDisconnected;

    public async Task ConnectAsync(string host, int port, CancellationToken token = default)
    {
        _cts = CancellationTokenSource.CreateLinkedTokenSource(token);
        _client = new TcpClient();
        await _client.ConnectAsync(host, port, _cts.Token);
        _stream = _client.GetStream();

        _ = Task.Run(() => ListenLoopAsync(_cts.Token));
    }

    public async Task SendPacketAsync(ControlPacket packet)
    {
        if (_stream == null || !IsConnected) return;

        string json = JsonSerializer.Serialize(packet);
        byte[] payloadBytes = Encoding.UTF8.GetBytes(json);
        byte[] headerBytes = BitConverter.GetBytes(payloadBytes.Length);

        /* Send 4-byte length header followed by packet data */
        await _stream.WriteAsync(headerBytes, 0, headerBytes.Length);
        await _stream.WriteAsync(payloadBytes, 0, payloadBytes.Length);
        await _stream.FlushAsync();
    }

    private async Task ListenLoopAsync(CancellationToken token)
    {
        byte[] lengthBuffer = new byte[4];

        try
        {
            while (!token.IsCancellationRequested && _stream != null)
            {
                int readBytes = await ReadExactAsync(_stream, lengthBuffer, 0, 4, token);
                if (readBytes == 0) break;

                int payloadLength = BitConverter.ToInt32(lengthBuffer, 0);
                if (payloadLength <= 0 || payloadLength > 10 * 1024 * 1024) break;

                byte[] payload = new byte[payloadLength];
                await ReadExactAsync(_stream, payload, 0, payloadLength, token);

                OnFrameReceived?.Invoke(payload);
            }
        }
        catch
        {
            /* Disconnect on stream read error */
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
            if (read == 0) return 0;
            totalRead += read;
        }
        return totalRead;
    }

    public async ValueTask DisposeAsync()
    {
        _cts?.Cancel();
        _stream?.Dispose();
        _client?.Dispose();
        await Task.CompletedTask;
    }
}
