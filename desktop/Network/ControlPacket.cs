using System.Text.Json.Serialization;

namespace Veyqo.Desktop.Network;

/* Remote control message envelope exchanged over TCP socket */
public enum ControlMessageType
{
    Handshake = 1,
    TouchDown = 2,
    TouchMove = 3,
    TouchUp = 4,
    KeyEvent = 5,
    TextInject = 6,
    Heartbeat = 7,
    Swipe = 8,
    Scroll = 9,
    NavBack = 10,
    NavHome = 11,
    NavRecents = 12,
    ClipboardSync = 13,
    FileChunk = 14,
    DeviceMetrics = 15,
    Disconnect = 16,
    SelectAll = 17,
    Copy = 18,
    Cut = 19,
    Paste = 20
}

public sealed class ControlPacket
{
    [JsonPropertyName("type")]
    public ControlMessageType Type { get; set; }

    [JsonPropertyName("x")]
    public float X { get; set; }

    [JsonPropertyName("y")]
    public float Y { get; set; }

    [JsonPropertyName("endX")]
    public float EndX { get; set; }

    [JsonPropertyName("endY")]
    public float EndY { get; set; }

    [JsonPropertyName("durationMs")]
    public long DurationMs { get; set; }

    [JsonPropertyName("keyCode")]
    public int KeyCode { get; set; }

    [JsonPropertyName("payload")]
    public string? Payload { get; set; }

    [JsonPropertyName("fileName")]
    public string? FileName { get; set; }

    [JsonPropertyName("fileData")]
    public string? FileData { get; set; }

    [JsonPropertyName("timestamp")]
    public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
}
