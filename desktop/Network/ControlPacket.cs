using System.Text.Json.Serialization;

namespace Veyqo.Desktop.Network;

/* Remote control message envelope exchanged over TCP/WebSocket */
public enum ControlMessageType
{
    Handshake = 1,
    TouchDown = 2,
    TouchMove = 3,
    TouchUp = 4,
    KeyEvent = 5,
    TextInject = 6,
    Heartbeat = 7,
    DeviceMetrics = 8,
    Disconnect = 9
}

public sealed class ControlPacket
{
    [JsonPropertyName("type")]
    public ControlMessageType Type { get; set; }

    [JsonPropertyName("x")]
    public float X { get; set; }

    [JsonPropertyName("y")]
    public float Y { get; set; }

    [JsonPropertyName("keyCode")]
    public int KeyCode { get; set; }

    [JsonPropertyName("payload")]
    public string? Payload { get; set; }

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
}
