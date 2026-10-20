using System.Text.Json;
using System.Text.Json.Serialization;

namespace TouchDeckCompanion.Models;

/// <summary>
/// Represents a TouchDeck protocol message.
/// All communication uses this envelope format.
/// </summary>
public class TouchDeckMessage
{
    [JsonPropertyName("v")]
    public int Version { get; set; } = 1;

    [JsonPropertyName("t")]
    public string Type { get; set; } = "";

    [JsonPropertyName("seq")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingDefault)]
    public long Sequence { get; set; }

    [JsonPropertyName("ts")]
    public long Timestamp { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    [JsonPropertyName("p")]
    public JsonElement? Payload { get; set; }

    public T? GetPayload<T>() where T : class
    {
        if (Payload == null) return null;
        return JsonSerializer.Deserialize<T>(Payload.Value.GetRawText(), JsonOptions.Default);
    }

    public static TouchDeckMessage Create(string type, object? payload = null)
    {
        var msg = new TouchDeckMessage
        {
            Type = type,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };

        if (payload != null)
        {
            var json = JsonSerializer.Serialize(payload, JsonOptions.Default);
            msg.Payload = JsonDocument.Parse(json).RootElement;
        }

        return msg;
    }

    public string Serialize() => JsonSerializer.Serialize(this, JsonOptions.Default);

    public static TouchDeckMessage? Deserialize(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<TouchDeckMessage>(json, JsonOptions.Default);
        }
        catch
        {
            return null;
        }
    }
}

public static class JsonOptions
{
    public static readonly JsonSerializerOptions Default = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        WriteIndented = false
    };
}

// --- Payload Models ---

public class PairRequestPayload
{
    [JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = "";

    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = "";

    [JsonPropertyName("app_version")]
    public string AppVersion { get; set; } = "1.0.0";
}

public class PairChallengePayload
{
    [JsonPropertyName("code")]
    public string Code { get; set; } = "";
}

public class PairConfirmPayload
{
    [JsonPropertyName("code")]
    public string Code { get; set; } = "";

    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = "";
}

public class PairResultPayload
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("token")]
    public string Token { get; set; } = "";

    [JsonPropertyName("server_name")]
    public string ServerName { get; set; } = "";

    [JsonPropertyName("error")]
    public string? Error { get; set; }
}

public class AuthPayload
{
    [JsonPropertyName("token")]
    public string? Token { get; set; }

    [JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = "";

    [JsonPropertyName("auth_response")]
    public string? AuthResponse { get; set; }

    [JsonPropertyName("nonce")]
    public string? Nonce { get; set; }
}

public class AuthChallengePayload
{
    [JsonPropertyName("nonce")]
    public string Nonce { get; set; } = "";
}

public class AuthResultPayload
{
    [JsonPropertyName("success")]
    public bool Success { get; set; }

    [JsonPropertyName("error")]
    public string? Error { get; set; }
}

public class MouseMovePayload
{
    [JsonPropertyName("dx")]
    public double Dx { get; set; }

    [JsonPropertyName("dy")]
    public double Dy { get; set; }
}

public class MouseButtonPayload
{
    [JsonPropertyName("btn")]
    public string Button { get; set; } = "left";
}

public class MouseScrollPayload
{
    [JsonPropertyName("dx")]
    public double Dx { get; set; }

    [JsonPropertyName("dy")]
    public double Dy { get; set; }
}

public class KeyPayload
{
    [JsonPropertyName("key")]
    public int Key { get; set; }

    [JsonPropertyName("code")]
    public string Code { get; set; } = "";
}

public class TextInputPayload
{
    [JsonPropertyName("text")]
    public string Text { get; set; } = "";
}

public class MediaActionPayload
{
    [JsonPropertyName("action")]
    public string Action { get; set; } = "";
}

public class ControllerButtonPayload
{
    [JsonPropertyName("btn")]
    public string Button { get; set; } = "";
}

public class ControllerStickPayload
{
    [JsonPropertyName("stick")]
    public string Stick { get; set; } = "left";

    [JsonPropertyName("x")]
    public double X { get; set; }

    [JsonPropertyName("y")]
    public double Y { get; set; }
}

public class StatusPayload
{
    [JsonPropertyName("status")]
    public string Status { get; set; } = "";

    [JsonPropertyName("message")]
    public string? Message { get; set; }
}

public class VibrationPayload
{
    [JsonPropertyName("large_motor")]
    public int LargeMotor { get; set; }

    [JsonPropertyName("small_motor")]
    public int SmallMotor { get; set; }
}

/// <summary>
/// Represents a paired device stored in settings.
/// </summary>
public class PairedDevice
{
    public string DeviceId { get; set; } = "";
    public string DeviceName { get; set; } = "";
    public string Token { get; set; } = "";
    public DateTime PairedAt { get; set; } = DateTime.UtcNow;
    public DateTime LastConnected { get; set; } = DateTime.UtcNow;
}

/// <summary>
/// Protocol message type constants.
/// </summary>
public static class MessageTypes
{
    public const string PairRequest = "pair_request";
    public const string PairChallenge = "pair_challenge";
    public const string PairConfirm = "pair_confirm";
    public const string PairResult = "pair_result";
    public const string AuthChallenge = "auth_challenge";
    public const string Auth = "auth";
    public const string AuthResult = "auth_result";
    public const string MouseMove = "mouse_move";
    public const string MouseButtonDown = "mouse_btn_down";
    public const string MouseButtonUp = "mouse_btn_up";
    public const string MouseScroll = "mouse_scroll";
    public const string KeyDown = "key_down";
    public const string KeyUp = "key_up";
    public const string TextInput = "text_input";
    public const string MediaAction = "media_action";
    public const string ControllerButtonDown = "ctrl_btn_down";
    public const string ControllerButtonUp = "ctrl_btn_up";
    public const string ControllerStick = "ctrl_stick";
    public const string Ping = "ping";
    public const string Pong = "pong";
    public const string Status = "status";
    public const string Disconnect = "disconnect";
    public const string Vibrate = "vibrate";
}
