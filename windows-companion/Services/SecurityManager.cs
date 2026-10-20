using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using TouchDeckCompanion.Models;

namespace TouchDeckCompanion.Services;

/// <summary>
/// Manages device pairing, authentication, and session tokens.
/// Stores paired devices using DPAPI-protected local storage.
/// </summary>
public class SecurityManager
{
    private readonly string _storagePath;
    private readonly Dictionary<string, PairedDevice> _pairedDevices = new();
    private readonly Dictionary<string, (string Code, DateTime Expires)> _pendingPairings = new();
    private readonly Dictionary<string, DateTime> _pairAttempts = new();
    private readonly Dictionary<string, (int Count, DateTime LastAttempt)> _confirmFailures = new();
    private readonly HashSet<string> _revokedDeviceIds = new();
    private readonly object _lock = new();

    private const int MaxPairAttemptsPerMinute = 10;
    private const int MaxConfirmFailures = 5;
    private const int ConfirmLockoutSeconds = 60;
    private const int PairingCodeLength = 6;
    private const int PairingTimeoutSeconds = 120;
    private const int TokenLengthBytes = 32;

    public event Action<string, string>? OnPairingRequested; // deviceId, deviceName
    public event Action<string>? OnPairingCodeGenerated;
    /// <summary>
    /// Raised when a device is revoked so the server can disconnect active sessions.
    /// </summary>
    public event Action<string>? OnDeviceRevoked;

    public string? ActivePairingCode { get; private set; }
    public string? ActivePairingDeviceId { get; private set; }

    public SecurityManager()
    {
        _storagePath = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "TouchDeck", "paired_devices.json");

        Directory.CreateDirectory(Path.GetDirectoryName(_storagePath)!);
        LoadPairedDevices();
    }

    /// <summary>
    /// Process a pairing request from a device.
    /// Returns a pairing challenge code to display on the PC.
    /// If the same device already has a pending pairing, reuse the existing code
    /// instead of generating a new one (prevents PIN-reset spam).
    /// </summary>
    public (bool Allowed, string? Code, string? Error) HandlePairRequest(string deviceId, string deviceName)
    {
        lock (_lock)
        {
            // Rate limiting across all devices
            CleanupExpiredAttempts();
            var attemptCount = _pairAttempts.Count(a => a.Value > DateTime.UtcNow.AddMinutes(-1));
            if (attemptCount >= MaxPairAttemptsPerMinute)
            {
                return (false, null, "Too many pairing attempts. Please wait.");
            }

            _pairAttempts[deviceId] = DateTime.UtcNow;

            // If ANY pairing PIN is currently active and unexpired, reuse the same code.
            // This prevents an attacker or burst of requests from churning/resetting the visible PIN on PC.
            if (!string.IsNullOrEmpty(ActivePairingCode))
            {
                var active = _pendingPairings.Values.FirstOrDefault(p => p.Code == ActivePairingCode && DateTime.UtcNow <= p.Expires);
                if (!string.IsNullOrEmpty(active.Code))
                {
                    _pendingPairings[deviceId] = (active.Code, active.Expires);
                    OnPairingRequested?.Invoke(deviceId, deviceName);
                    return (true, active.Code, null);
                }
            }

            // Generate 6-digit pairing code
            var code = GeneratePairingCode();
            _pendingPairings[deviceId] = (code, DateTime.UtcNow.AddSeconds(PairingTimeoutSeconds));
            ActivePairingCode = code;
            ActivePairingDeviceId = deviceId;

            OnPairingRequested?.Invoke(deviceId, deviceName);
            OnPairingCodeGenerated?.Invoke(code);

            return (true, code, null);
        }
    }

    /// <summary>
    /// Confirm a pairing with the code entered by the user.
    /// Returns a session token if successful.
    /// Rate-limits failed PIN confirmations to prevent brute-force attacks.
    /// </summary>
    public (bool Success, string? Token, string? Error) ConfirmPairing(string deviceId, string code, string deviceName)
    {
        lock (_lock)
        {
            // Check for confirmation rate limiting (brute-force protection)
            if (_confirmFailures.TryGetValue(deviceId, out var failures))
            {
                if (failures.Count >= MaxConfirmFailures &&
                    (DateTime.UtcNow - failures.LastAttempt).TotalSeconds < ConfirmLockoutSeconds)
                {
                    return (false, null, $"Too many failed attempts. Try again in {ConfirmLockoutSeconds} seconds.");
                }

                // Reset counter if lockout period has passed
                if ((DateTime.UtcNow - failures.LastAttempt).TotalSeconds >= ConfirmLockoutSeconds)
                {
                    _confirmFailures.Remove(deviceId);
                }
            }

            if (!_pendingPairings.TryGetValue(deviceId, out var pending))
            {
                return (false, null, "No pending pairing request.");
            }

            if (DateTime.UtcNow > pending.Expires)
            {
                _pendingPairings.Remove(deviceId);
                ActivePairingCode = null;
                ActivePairingDeviceId = null;
                return (false, null, "Pairing request expired.");
            }

            // Use constant-time comparison to prevent timing attacks
            if (!CryptographicOperations.FixedTimeEquals(
                System.Text.Encoding.UTF8.GetBytes(pending.Code),
                System.Text.Encoding.UTF8.GetBytes(code)))
            {
                // Track failed attempt
                if (_confirmFailures.TryGetValue(deviceId, out var existing))
                {
                    _confirmFailures[deviceId] = (existing.Count + 1, DateTime.UtcNow);
                }
                else
                {
                    _confirmFailures[deviceId] = (1, DateTime.UtcNow);
                }

                var remaining = MaxConfirmFailures - (_confirmFailures[deviceId].Count);
                if (remaining <= 0)
                {
                    // Invalidate the pending pairing on lockout
                    _pendingPairings.Remove(deviceId);
                    ActivePairingCode = null;
                    ActivePairingDeviceId = null;
                    return (false, null, $"Too many failed attempts. Pairing cancelled. Try again in {ConfirmLockoutSeconds} seconds.");
                }
                return (false, null, $"Invalid pairing code. {remaining} attempts remaining.");
            }

            // Success — clear failure tracking
            _confirmFailures.Remove(deviceId);

            // Clear from revoked list if re-pairing
            _revokedDeviceIds.Remove(deviceId);

            // Generate session token
            var token = GenerateSessionToken();
            var device = new PairedDevice
            {
                DeviceId = deviceId,
                DeviceName = deviceName,
                Token = token,
                PairedAt = DateTime.UtcNow,
                LastConnected = DateTime.UtcNow
            };

            _pairedDevices[deviceId] = device;
            _pendingPairings.Remove(deviceId);
            ActivePairingCode = null;
            ActivePairingDeviceId = null;
            SavePairedDevices();

            return (true, token, null);
        }
    }

    /// <summary>
    /// Authenticate a returning device with its session token.
    /// </summary>
    public (bool Success, string? Error) Authenticate(string deviceId, string token)
    {
        lock (_lock)
        {
            // Reject revoked devices even if they still have a valid token cached
            if (_revokedDeviceIds.Contains(deviceId))
            {
                return (false, "Device has been revoked. Please pair again.");
            }

            if (!_pairedDevices.TryGetValue(deviceId, out var device))
            {
                return (false, "Device not paired. Please pair first.");
            }

            // Use constant-time comparison for token validation
            if (!CryptographicOperations.FixedTimeEquals(
                System.Text.Encoding.UTF8.GetBytes(device.Token),
                System.Text.Encoding.UTF8.GetBytes(token)))
            {
                return (false, "Invalid session token.");
            }

            device.LastConnected = DateTime.UtcNow;
            SavePairedDevices();
            return (true, null);
        }
    }

    /// <summary>
    /// Authenticate a device using challenge-response HMAC-SHA256.
    /// The device proves it possesses the token without ever sending the plaintext token over the network.
    /// </summary>
    public (bool Success, string? Error) AuthenticateHmac(string deviceId, string nonce, string authResponse)
    {
        lock (_lock)
        {
            if (_revokedDeviceIds.Contains(deviceId))
            {
                return (false, "Device has been revoked. Please pair again.");
            }

            if (!_pairedDevices.TryGetValue(deviceId, out var device))
            {
                return (false, "Device not paired. Please pair first.");
            }

            // Compute expected HMAC-SHA256(key = device.Token, data = nonce)
            using var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(device.Token));
            var expectedHash = hmac.ComputeHash(Encoding.UTF8.GetBytes(nonce));
            var expectedHex = Convert.ToHexString(expectedHash).ToLowerInvariant();

            // Constant-time comparison prevents timing attacks
            if (!CryptographicOperations.FixedTimeEquals(
                Encoding.UTF8.GetBytes(expectedHex),
                Encoding.UTF8.GetBytes(authResponse.ToLowerInvariant())))
            {
                return (false, "Invalid challenge response signature.");
            }

            device.LastConnected = DateTime.UtcNow;
            SavePairedDevices();
            return (true, null);
        }
    }

    /// <summary>
    /// Check if a device has been revoked (used by the server to reject
    /// already-authenticated sessions mid-flight).
    /// </summary>
    public bool IsDeviceRevoked(string deviceId)
    {
        lock (_lock)
        {
            return _revokedDeviceIds.Contains(deviceId) || !_pairedDevices.ContainsKey(deviceId);
        }
    }

    /// <summary>
    /// Revoke a paired device. Fires OnDeviceRevoked so the server
    /// can forcibly disconnect any active session for that device.
    /// </summary>
    public bool RevokeDevice(string deviceId)
    {
        lock (_lock)
        {
            if (_pairedDevices.Remove(deviceId))
            {
                _revokedDeviceIds.Add(deviceId);
                SavePairedDevices();
                OnDeviceRevoked?.Invoke(deviceId);
                return true;
            }
            return false;
        }
    }

    /// <summary>
    /// Get all paired devices.
    /// </summary>
    public IReadOnlyList<PairedDevice> GetPairedDevices()
    {
        lock (_lock)
        {
            return _pairedDevices.Values.ToList().AsReadOnly();
        }
    }

    private string GeneratePairingCode()
    {
        var bytes = RandomNumberGenerator.GetBytes(4);
        var num = BitConverter.ToUInt32(bytes) % 1000000;
        return num.ToString("D6");
    }

    private string GenerateSessionToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(TokenLengthBytes);
        return Convert.ToBase64String(bytes);
    }

    private void CleanupExpiredAttempts()
    {
        var cutoff = DateTime.UtcNow.AddMinutes(-1);
        var expired = _pairAttempts.Where(a => a.Value < cutoff).Select(a => a.Key).ToList();
        foreach (var key in expired)
        {
            _pairAttempts.Remove(key);
        }

        // Cleanup expired pending pairings
        var expiredPairings = _pendingPairings.Where(p => DateTime.UtcNow > p.Value.Expires)
            .Select(p => p.Key).ToList();
        foreach (var key in expiredPairings)
        {
            _pendingPairings.Remove(key);
        }

        // Cleanup old confirm failure records (older than lockout window)
        var expiredFailures = _confirmFailures
            .Where(f => (DateTime.UtcNow - f.Value.LastAttempt).TotalSeconds >= ConfirmLockoutSeconds * 2)
            .Select(f => f.Key).ToList();
        foreach (var key in expiredFailures)
        {
            _confirmFailures.Remove(key);
        }
    }

    private void LoadPairedDevices()
    {
        try
        {
            if (File.Exists(_storagePath))
            {
                // Read DPAPI-protected data
                var encryptedBytes = File.ReadAllBytes(_storagePath);
                byte[] decryptedBytes;
                try
                {
                    decryptedBytes = ProtectedData.Unprotect(encryptedBytes, null, DataProtectionScope.CurrentUser);
                }
                catch
                {
                    // DPAPI decryption failed — treat file as corrupted rather than
                    // falling back to plaintext (which would weaken at-rest protection).
                    System.Diagnostics.Debug.WriteLine("DPAPI decryption failed. Paired devices file may be corrupted. Starting fresh.");
                    return;
                }
                var json = System.Text.Encoding.UTF8.GetString(decryptedBytes);
                var devices = JsonSerializer.Deserialize<List<PairedDevice>>(json);
                if (devices != null)
                {
                    foreach (var d in devices)
                    {
                        _pairedDevices[d.DeviceId] = d;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Failed to load paired devices: {ex.Message}");
        }
    }

    private void SavePairedDevices()
    {
        try
        {
            var json = JsonSerializer.Serialize(_pairedDevices.Values.ToList());
            var jsonBytes = System.Text.Encoding.UTF8.GetBytes(json);
            var encryptedBytes = ProtectedData.Protect(jsonBytes, null, DataProtectionScope.CurrentUser);
            File.WriteAllBytes(_storagePath, encryptedBytes);
        }
        catch (Exception ex)
        {
            System.Diagnostics.Debug.WriteLine($"Failed to save paired devices: {ex.Message}");
        }
    }
}
