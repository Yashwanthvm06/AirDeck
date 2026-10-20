using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using System.Collections.Concurrent;
using TouchDeckCompanion.Models;

namespace TouchDeckCompanion.Services;

/// <summary>
/// WebSocket server that listens for TouchDeck client connections.
/// Uses native TcpListener with RFC 6455 WebSocket framing, avoiding
/// Windows HTTP.sys URL ACL permission restrictions and hostname binding issues.
/// </summary>
public class WebSocketServer : IDisposable
{
    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private readonly ConcurrentDictionary<string, ClientSession> _sessions = new();
    private readonly InputSimulator _inputSimulator;
    private readonly SecurityManager _securityManager;
    private readonly VirtualGamepadService _virtualGamepadService;
    private readonly int _port;
    private bool _isRunning;

    // Per-session input tracking — each client gets its own held-input state
    private readonly ConcurrentDictionary<string, SessionInputState> _sessionInputStates = new();

    // Unauthenticated connection tracking to prevent resource exhaustion
    private int _unauthenticatedConnectionCount;
    private const int MaxUnauthenticatedConnections = 10;
    private static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(15);
    private static readonly TimeSpan AuthTimeout = TimeSpan.FromSeconds(30);

    private const int MaxMessageSize = 4096;
    // Atomic client slot reservation to prevent concurrent connection bursts exceeding limit
    private int _connectedClientSlots;
    private const int MaxConcurrentClients = 5;

    // Input value clamping bounds
    private const double MaxMouseDelta = 500.0;
    private const double MaxScrollDelta = 50.0;

    public bool IsRunning => _isRunning;
    public int Port => _port;
    public VirtualGamepadService GamepadService => _virtualGamepadService;

    public event Action<string>? OnLog;
    public event Action<bool>? OnServerStateChanged;
    public event Action<string, string>? OnClientConnected;    // deviceId, deviceName
    public event Action<string>? OnClientDisconnected;         // deviceId
    public event Action<string>? OnPairingCodeGenerated;

    public WebSocketServer(InputSimulator inputSimulator, SecurityManager securityManager, VirtualGamepadService? virtualGamepadService = null, int port = 9876)
    {
        _inputSimulator = inputSimulator;
        _securityManager = securityManager;
        _virtualGamepadService = virtualGamepadService ?? new VirtualGamepadService(inputSimulator);
        _port = port;

        _virtualGamepadService.OnLog += msg => Log(msg);

        // Forward game rumble/vibration to all connected Android clients
        _virtualGamepadService.OnVibrationReceived += (largeMotor, smallMotor) =>
        {
            _ = BroadcastVibrationAsync(largeMotor, smallMotor);
        };

        _securityManager.OnPairingCodeGenerated += code =>
        {
            OnPairingCodeGenerated?.Invoke(code);
        };

        // When a device is revoked, forcibly disconnect its active session
        _securityManager.OnDeviceRevoked += deviceId =>
        {
            _ = DisconnectDeviceAsync(deviceId);
        };
    }

    /// <summary>
    /// Start the WebSocket server on the configured port.
    /// </summary>
    public Task StartAsync()
    {
        if (_isRunning) return Task.CompletedTask;

        _cts = new CancellationTokenSource();
        _listener = new TcpListener(IPAddress.Any, _port);
        _listener.Start();

        _isRunning = true;
        OnServerStateChanged?.Invoke(true);
        Log($"AirDesk server started on port {_port}");
        Log($"Local IP: {GetLocalIPAddress()}");

        _ = AcceptClientsAsync(_cts.Token);
        return Task.CompletedTask;
    }

    /// <summary>
    /// Stop the WebSocket server and disconnect all clients.
    /// </summary>
    public async Task StopAsync()
    {
        if (!_isRunning) return;

        _cts?.Cancel();

        // Disconnect all clients and release each session's tracked input
        foreach (var session in _sessions.Values)
        {
            try
            {
                ReleaseSessionInput(session.SessionId);
                if (session.Socket.State == WebSocketState.Open)
                {
                    await session.Socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Server stopping",
                        CancellationToken.None);
                }
            }
            catch { /* Ignore errors during shutdown */ }
        }
        _sessions.Clear();
        _sessionInputStates.Clear();

        try { _listener?.Stop(); } catch { }
        _listener = null;

        _isRunning = false;
        OnServerStateChanged?.Invoke(false);
        Log("AirDesk server stopped");
    }

    /// <summary>
    /// Force-disconnect all active sessions for a given device ID.
    /// Called when a device is revoked.
    /// </summary>
    private async Task DisconnectDeviceAsync(string deviceId)
    {
        var sessionsToClose = _sessions.Values
            .Where(s => s.DeviceId == deviceId)
            .ToList();

        foreach (var session in sessionsToClose)
        {
            try
            {
                ReleaseSessionInput(session.SessionId);
                if (session.Socket.State == WebSocketState.Open)
                {
                    await session.Socket.CloseAsync(
                        WebSocketCloseStatus.NormalClosure,
                        "Device revoked",
                        CancellationToken.None);
                }
            }
            catch { /* Ignore errors during forced disconnect */ }
            finally
            {
                _sessions.TryRemove(session.SessionId, out _);
            }
        }

        if (sessionsToClose.Count > 0)
        {
            OnClientDisconnected?.Invoke(deviceId);
            Log($"Revoked device {deviceId} disconnected ({sessionsToClose.Count} sessions)");
        }
    }

    private async Task AcceptClientsAsync(CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && _listener != null)
        {
            try
            {
                var tcpClient = await _listener.AcceptTcpClientAsync(ct);
                tcpClient.NoDelay = true; // Low-latency touch/mouse input
                _ = HandleIncomingConnectionAsync(tcpClient, ct);
            }
            catch (OperationCanceledException) { break; }
            catch (ObjectDisposedException) { break; }
            catch (SocketException) when (ct.IsCancellationRequested) { break; }
            catch (Exception ex)
            {
                if (!ct.IsCancellationRequested)
                {
                    Log($"Error accepting client: {ex.Message}");
                    await Task.Delay(100, ct);
                }
            }
        }
    }

    private async Task HandleIncomingConnectionAsync(TcpClient tcpClient, CancellationToken ct)
    {
        // Track unauthenticated connections to prevent resource exhaustion
        var unauthCount = Interlocked.Increment(ref _unauthenticatedConnectionCount);
        if (unauthCount > MaxUnauthenticatedConnections)
        {
            Interlocked.Decrement(ref _unauthenticatedConnectionCount);
            Log("Rejected connection: too many unauthenticated connections");
            try { tcpClient.Close(); } catch { }
            return;
        }

        var remoteEndpoint = tcpClient.Client.RemoteEndPoint as IPEndPoint;
        var stream = tcpClient.GetStream();

        try
        {
            // Read HTTP request headers with a timeout to prevent slow-loris attacks
            using var handshakeCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            handshakeCts.CancelAfter(HandshakeTimeout);

            var (isWebSocket, key, path) = await ReadHandshakeAsync(stream, handshakeCts.Token);

            if (!isWebSocket || string.IsNullOrEmpty(key))
            {
                // Serve friendly HTTP response if accessed via browser or health check
                var body = Encoding.UTF8.GetBytes(
                    "AirDesk Companion Server is running. Connect using the AirDesk Android app.\n");
                var httpResponse =
                    "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain; charset=utf-8\r\n" +
                    $"Content-Length: {body.Length}\r\n" +
                    "Connection: close\r\n\r\n";
                var headerBytes = Encoding.UTF8.GetBytes(httpResponse);
                await stream.WriteAsync(headerBytes, ct);
                await stream.WriteAsync(body, ct);
                await stream.FlushAsync(ct);
                return;
            }

            // Atomic slot reservation: prevents concurrent connection bursts from bypassing the client limit
            if (Interlocked.Increment(ref _connectedClientSlots) > MaxConcurrentClients)
            {
                Interlocked.Decrement(ref _connectedClientSlots);
                var resp503 = "HTTP/1.1 503 Service Unavailable\r\nConnection: close\r\n\r\n";
                await stream.WriteAsync(Encoding.UTF8.GetBytes(resp503), ct);
                await stream.FlushAsync(ct);
                Log("Rejected connection: max concurrent clients reached");
                return;
            }

            try
            {
                // Complete WebSocket RFC 6455 handshake
                await CompleteHandshakeAsync(stream, key, ct);

                // Create managed WebSocket stream
                using var webSocket = WebSocket.CreateFromStream(stream, isServer: true, subProtocol: null, TimeSpan.FromSeconds(30));

                // Run client message loop
                await HandleClientAsync(webSocket, remoteEndpoint, ct);
            }
            finally
            {
                Interlocked.Decrement(ref _connectedClientSlots);
            }
        }
        catch (OperationCanceledException) { }
        catch (Exception ex)
        {
            Log($"Connection error from {remoteEndpoint}: {ex.Message}");
        }
        finally
        {
            Interlocked.Decrement(ref _unauthenticatedConnectionCount);
            try { tcpClient.Close(); } catch { }
        }
    }

    private static async Task<(bool IsWebSocket, string? Key, string Path)> ReadHandshakeAsync(Stream stream, CancellationToken ct)
    {
        var headerBytes = new List<byte>(1024);
        var singleByte = new byte[1];

        while (!ct.IsCancellationRequested && headerBytes.Count < 8192)
        {
            int read = await stream.ReadAsync(singleByte.AsMemory(0, 1), ct);
            if (read == 0) return (false, null, "");

            headerBytes.Add(singleByte[0]);

            int count = headerBytes.Count;
            if (count >= 4 &&
                headerBytes[count - 4] == (byte)'\r' &&
                headerBytes[count - 3] == (byte)'\n' &&
                headerBytes[count - 2] == (byte)'\r' &&
                headerBytes[count - 1] == (byte)'\n')
            {
                break;
            }
        }

        string headers = Encoding.UTF8.GetString(headerBytes.ToArray());
        var lines = headers.Split(new[] { "\r\n" }, StringSplitOptions.None);
        if (lines.Length == 0) return (false, null, "");

        var firstLineParts = lines[0].Split(' ');
        string path = firstLineParts.Length > 1 ? firstLineParts[1] : "/";

        string? key = null;
        bool isUpgrade = false;
        bool isWebSocket = false;

        foreach (var line in lines)
        {
            if (line.StartsWith("Upgrade:", StringComparison.OrdinalIgnoreCase) &&
                line.IndexOf("websocket", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                isWebSocket = true;
            }
            else if (line.StartsWith("Connection:", StringComparison.OrdinalIgnoreCase) &&
                     line.IndexOf("Upgrade", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                isUpgrade = true;
            }
            else if (line.StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
            {
                key = line.Substring("Sec-WebSocket-Key:".Length).Trim();
            }
        }

        return (isWebSocket && isUpgrade && !string.IsNullOrEmpty(key), key, path);
    }

    private static async Task CompleteHandshakeAsync(Stream stream, string secWebSocketKey, CancellationToken ct)
    {
        const string magicGuid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        string combined = secWebSocketKey + magicGuid;
        byte[] hash = SHA1.HashData(Encoding.UTF8.GetBytes(combined));
        string acceptKey = Convert.ToBase64String(hash);

        string response =
            "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            $"Sec-WebSocket-Accept: {acceptKey}\r\n\r\n";

        byte[] responseBytes = Encoding.UTF8.GetBytes(response);
        await stream.WriteAsync(responseBytes, ct);
        await stream.FlushAsync(ct);
    }

    private async Task HandleClientAsync(WebSocket socket, IPEndPoint? remoteEndpoint, CancellationToken ct)
    {
        string sessionId = Guid.NewGuid().ToString("N")[..8];
        string connectionNonce = Convert.ToHexString(RandomNumberGenerator.GetBytes(32)).ToLowerInvariant();
        string? authenticatedDeviceId = null;
        string? deviceName = null;
        bool isAuthenticated = false;

        Log($"New connection from {remoteEndpoint} (session {sessionId})");

        // Send auth challenge immediately to client for zero-plaintext HMAC authentication
        await SendMessageAsync(socket, TouchDeckMessage.Create(MessageTypes.AuthChallenge, new AuthChallengePayload { Nonce = connectionNonce }));

        // Enforce authentication timeout — if client doesn't authenticate within
        // AuthTimeout, the connection is closed to free resources.
        using var authTimeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        if (!isAuthenticated) authTimeoutCts.CancelAfter(AuthTimeout);

        var buffer = new byte[MaxMessageSize];
        // Accumulate WebSocket fragments into a complete message
        using var messageStream = new MemoryStream(MaxMessageSize);

        try
        {
            while (socket.State == WebSocketState.Open && !authTimeoutCts.IsCancellationRequested)
            {
                var result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), authTimeoutCts.Token);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Goodbye", CancellationToken.None);
                    break;
                }

                if (result.MessageType != WebSocketMessageType.Text) continue;

                // Accumulate fragments until EndOfMessage
                messageStream.Write(buffer, 0, result.Count);

                if (!result.EndOfMessage)
                {
                    // Reject if accumulated fragments exceed max message size
                    if (messageStream.Length > MaxMessageSize)
                    {
                        Log($"Rejected oversized fragmented message from {sessionId}");
                        messageStream.SetLength(0);
                        // Skip remaining fragments
                        while (!result.EndOfMessage && socket.State == WebSocketState.Open)
                        {
                            result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), authTimeoutCts.Token);
                        }
                    }
                    continue;
                }

                // Full message assembled — check size
                if (messageStream.Length > MaxMessageSize)
                {
                    Log($"Rejected oversized message from {sessionId}");
                    messageStream.SetLength(0);
                    continue;
                }

                var json = Encoding.UTF8.GetString(messageStream.GetBuffer(), 0, (int)messageStream.Length);
                messageStream.SetLength(0); // Reset for next message

                var message = TouchDeckMessage.Deserialize(json);

                if (message == null)
                {
                    Log($"Malformed message from {sessionId}");
                    continue;
                }

                // Version check
                if (message.Version != 1)
                {
                    await SendMessageAsync(socket,
                        TouchDeckMessage.Create(MessageTypes.Status,
                            new StatusPayload { Status = "error", Message = "Unsupported protocol version" }));
                    continue;
                }

                // Handle unauthenticated messages
                if (!isAuthenticated)
                {
                    switch (message.Type)
                    {
                        case MessageTypes.PairRequest:
                            var pairReq = message.GetPayload<PairRequestPayload>();
                            if (pairReq == null) break;

                            deviceName = pairReq.DeviceName;
                            var (allowed, code, error) = _securityManager.HandlePairRequest(
                                pairReq.DeviceId, pairReq.DeviceName);

                            if (allowed)
                            {
                                // Send challenge without revealing code to ensure visual PIN verification
                                await SendMessageAsync(socket,
                                    TouchDeckMessage.Create(MessageTypes.PairChallenge,
                                        new PairChallengePayload { Code = "" }));
                                Log($"Pairing requested by {pairReq.DeviceName} - PIN shown on PC screen: {code}");
                            }
                            else
                            {
                                await SendMessageAsync(socket,
                                    TouchDeckMessage.Create(MessageTypes.PairResult,
                                        new PairResultPayload { Success = false, Error = error }));
                            }
                            break;

                        case MessageTypes.PairConfirm:
                            var confirm = message.GetPayload<PairConfirmPayload>();
                            if (confirm == null) break;

                            var (success, token, confirmError) = _securityManager.ConfirmPairing(
                                confirm.DeviceId, confirm.Code, deviceName ?? "Unknown");

                            await SendMessageAsync(socket,
                                TouchDeckMessage.Create(MessageTypes.PairResult,
                                    new PairResultPayload
                                    {
                                        Success = success,
                                        Token = token ?? "",
                                        ServerName = Environment.MachineName,
                                        Error = confirmError
                                    }));

                            if (success)
                            {
                                isAuthenticated = true;
                                authenticatedDeviceId = confirm.DeviceId;
                                var session = new ClientSession(sessionId, socket, confirm.DeviceId, deviceName ?? "Unknown");
                                _sessions[sessionId] = session;
                                // Cancel the auth timeout now that we're authenticated
                                authTimeoutCts.CancelAfter(Timeout.InfiniteTimeSpan);
                                OnClientConnected?.Invoke(confirm.DeviceId, deviceName ?? "Unknown");
                                Log($"Device {deviceName} paired and authenticated");
                            }
                            break;

                        case MessageTypes.Auth:
                            var auth = message.GetPayload<AuthPayload>();
                            if (auth == null) break;

                            bool authSuccess = false;
                            string? authError = null;

                            if (!string.IsNullOrEmpty(auth.AuthResponse) && !string.IsNullOrEmpty(auth.Nonce))
                            {
                                // Bind verification strictly to the server's current, single-use connection nonce.
                                // Invalidate immediately upon checking to make replay impossible.
                                if (string.IsNullOrEmpty(connectionNonce) || !CryptographicOperations.FixedTimeEquals(
                                    Encoding.UTF8.GetBytes(connectionNonce),
                                    Encoding.UTF8.GetBytes(auth.Nonce.ToLowerInvariant())))
                                {
                                    authSuccess = false;
                                    authError = "Invalid, mismatched, or expired connection challenge nonce.";
                                    Log($"Session {sessionId} rejected: challenge nonce mismatch or replay attempt");
                                }
                                else
                                {
                                    string nonceToVerify = connectionNonce;
                                    connectionNonce = ""; // Invalidate immediately: single-use enforcement
                                    (authSuccess, authError) = _securityManager.AuthenticateHmac(auth.DeviceId, nonceToVerify, auth.AuthResponse);
                                }
                            }
                            else if (!string.IsNullOrEmpty(auth.Token))
                            {
                                // Fallback for legacy clients
                                (authSuccess, authError) = _securityManager.Authenticate(auth.DeviceId, auth.Token);
                            }
                            else
                            {
                                authError = "Missing authentication credential.";
                            }

                            await SendMessageAsync(socket,
                                TouchDeckMessage.Create(MessageTypes.AuthResult,
                                    new AuthResultPayload { Success = authSuccess, Error = authError }));

                            if (authSuccess)
                            {
                                isAuthenticated = true;
                                authenticatedDeviceId = auth.DeviceId;
                                var pairedDevices = _securityManager.GetPairedDevices();
                                var dev = pairedDevices.FirstOrDefault(d => d.DeviceId == auth.DeviceId);
                                deviceName = dev?.DeviceName ?? "Unknown";
                                var session = new ClientSession(sessionId, socket, auth.DeviceId, deviceName);
                                _sessions[sessionId] = session;
                                // Cancel the auth timeout now that we're authenticated
                                authTimeoutCts.CancelAfter(Timeout.InfiniteTimeSpan);
                                OnClientConnected?.Invoke(auth.DeviceId, deviceName);
                                Log($"Device {deviceName} authenticated via HMAC challenge-response");
                            }
                            break;

                        case MessageTypes.Ping:
                            await SendMessageAsync(socket, TouchDeckMessage.Create(MessageTypes.Pong));
                            break;

                        default:
                            Log($"Rejected unauthenticated command '{message.Type}' from {sessionId}");
                            break;
                    }
                    continue;
                }

                // Check if device was revoked mid-session
                if (authenticatedDeviceId != null && _securityManager.IsDeviceRevoked(authenticatedDeviceId))
                {
                    Log($"Session {sessionId} device was revoked, disconnecting");
                    await SendMessageAsync(socket,
                        TouchDeckMessage.Create(MessageTypes.Status,
                            new StatusPayload { Status = "error", Message = "Device has been revoked." }));
                    break;
                }

                // Handle authenticated messages
                ProcessCommand(message, sessionId);

                // Handle ping even from authenticated clients
                if (message.Type == MessageTypes.Ping)
                {
                    await SendMessageAsync(socket, TouchDeckMessage.Create(MessageTypes.Pong));
                }
            }
        }
        catch (OperationCanceledException)
        {
            if (!isAuthenticated && !ct.IsCancellationRequested)
            {
                Log($"Session {sessionId} authentication timed out");
            }
        }
        catch (WebSocketException) { }
        catch (Exception ex)
        {
            Log($"Client error ({sessionId}): {ex.Message}");
        }
        finally
        {
            // Release only THIS session's held input — not all input globally
            ReleaseSessionInput(sessionId);
            _sessions.TryRemove(sessionId, out _);
            _sessionInputStates.TryRemove(sessionId, out _);

            if (authenticatedDeviceId != null)
            {
                OnClientDisconnected?.Invoke(authenticatedDeviceId);
                Log($"Device {deviceName} disconnected");
            }

            try
            {
                if (socket.State == WebSocketState.Open)
                {
                    await socket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Disconnected",
                        CancellationToken.None);
                }
            }
            catch { }

            socket.Dispose();
        }
    }

    /// <summary>
    /// Release only the input (keys + mouse buttons) held by a specific session,
    /// rather than releasing ALL global input which would affect other clients.
    /// </summary>
    private void ReleaseSessionInput(string sessionId)
    {
        if (_sessionInputStates.TryRemove(sessionId, out var state))
        {
            // Only release a key if NO OTHER active session has that key held
            foreach (var vk in state.HeldKeys)
            {
                bool heldByOther = _sessionInputStates.Values.Any(s => s.HeldKeys.Contains(vk));
                if (!heldByOther)
                {
                    _inputSimulator.KeyUp(vk);
                }
            }
            // Only release a mouse button if NO OTHER active session has it held
            foreach (var btn in state.HeldMouseButtons)
            {
                bool heldByOther = _sessionInputStates.Values.Any(s => s.HeldMouseButtons.Contains(btn));
                if (!heldByOther)
                {
                    _inputSimulator.MouseButtonUp(btn);
                }
            }
        }
        // Only reset the shared virtual gamepad if no other client session remains active
        if (_sessions.Count <= 1)
        {
            _virtualGamepadService.ReleaseAll();
        }
    }

    private void ProcessCommand(TouchDeckMessage message, string sessionId)
    {
        try
        {
            // Get or create per-session input state
            var inputState = _sessionInputStates.GetOrAdd(sessionId, _ => new SessionInputState());

            switch (message.Type)
            {
                case MessageTypes.MouseMove:
                    var move = message.GetPayload<MouseMovePayload>();
                    if (move != null)
                    {
                        // Clamp mouse deltas to prevent erratic input
                        var dx = Math.Clamp(move.Dx, -MaxMouseDelta, MaxMouseDelta);
                        var dy = Math.Clamp(move.Dy, -MaxMouseDelta, MaxMouseDelta);
                        if (double.IsFinite(dx) && double.IsFinite(dy))
                            _inputSimulator.MoveMouse(dx, dy);
                    }
                    break;

                case MessageTypes.MouseButtonDown:
                    var btnDown = message.GetPayload<MouseButtonPayload>();
                    if (btnDown != null)
                    {
                        _inputSimulator.MouseButtonDown(btnDown.Button);
                        inputState.HeldMouseButtons.Add(btnDown.Button);
                    }
                    break;

                case MessageTypes.MouseButtonUp:
                    var btnUp = message.GetPayload<MouseButtonPayload>();
                    if (btnUp != null)
                    {
                        _inputSimulator.MouseButtonUp(btnUp.Button);
                        inputState.HeldMouseButtons.Remove(btnUp.Button);
                    }
                    break;

                case MessageTypes.MouseScroll:
                    var scroll = message.GetPayload<MouseScrollPayload>();
                    if (scroll != null)
                    {
                        // Clamp scroll deltas to prevent erratic behavior
                        var sdx = Math.Clamp(scroll.Dx, -MaxScrollDelta, MaxScrollDelta);
                        var sdy = Math.Clamp(scroll.Dy, -MaxScrollDelta, MaxScrollDelta);
                        if (double.IsFinite(sdx) && double.IsFinite(sdy))
                            _inputSimulator.MouseScroll(sdx, sdy);
                    }
                    break;

                case MessageTypes.KeyDown:
                    var keyDown = message.GetPayload<KeyPayload>();
                    if (keyDown != null)
                    {
                        _inputSimulator.KeyDown((ushort)keyDown.Key);
                        inputState.HeldKeys.Add((ushort)keyDown.Key);
                    }
                    break;

                case MessageTypes.KeyUp:
                    var keyUp = message.GetPayload<KeyPayload>();
                    if (keyUp != null)
                    {
                        _inputSimulator.KeyUp((ushort)keyUp.Key);
                        inputState.HeldKeys.Remove((ushort)keyUp.Key);
                    }
                    break;

                case MessageTypes.TextInput:
                    var text = message.GetPayload<TextInputPayload>();
                    if (text != null && text.Text.Length <= 1000) _inputSimulator.TypeText(text.Text);
                    break;

                case MessageTypes.MediaAction:
                    var media = message.GetPayload<MediaActionPayload>();
                    if (media != null) _inputSimulator.SendMediaKey(media.Action);
                    break;

                case MessageTypes.ControllerButtonDown:
                    var ctrlDown = message.GetPayload<ControllerButtonPayload>();
                    if (ctrlDown != null) HandleControllerButton(ctrlDown.Button, true);
                    break;

                case MessageTypes.ControllerButtonUp:
                    var ctrlUp = message.GetPayload<ControllerButtonPayload>();
                    if (ctrlUp != null) HandleControllerButton(ctrlUp.Button, false);
                    break;

                case MessageTypes.ControllerStick:
                    var stick = message.GetPayload<ControllerStickPayload>();
                    if (stick != null)
                    {
                        // Clamp stick values to -1.0 .. 1.0 range
                        var sx = Math.Clamp(stick.X, -1.0, 1.0);
                        var sy = Math.Clamp(stick.Y, -1.0, 1.0);
                        if (double.IsFinite(sx) && double.IsFinite(sy))
                            _virtualGamepadService.HandleStick(stick.Stick, sx, sy);
                    }
                    break;

                case MessageTypes.Disconnect:
                    ReleaseSessionInput(sessionId);
                    break;

                // Ping is handled in the main loop
            }
        }
        catch (Exception ex)
        {
            Log($"Error processing {message.Type}: {ex.Message}");
        }
    }

    private void HandleControllerButton(string button, bool isDown)
    {
        _virtualGamepadService.HandleButton(button, isDown);
    }

    /// <summary>
    /// Broadcast game rumble/vibration feedback to all connected clients.
    /// Called when a game sends force feedback to the virtual Xbox 360 controller.
    /// </summary>
    private async Task BroadcastVibrationAsync(byte largeMotor, byte smallMotor)
    {
        var message = TouchDeckMessage.Create(MessageTypes.Vibrate,
            new VibrationPayload { LargeMotor = largeMotor, SmallMotor = smallMotor });

        foreach (var session in _sessions.Values)
        {
            try
            {
                await SendMessageAsync(session.Socket, message);
            }
            catch { /* Client may have disconnected */ }
        }
    }

    private async Task SendMessageAsync(WebSocket socket, TouchDeckMessage message)
    {
        if (socket.State != WebSocketState.Open) return;

        var json = message.Serialize();
        var bytes = Encoding.UTF8.GetBytes(json);
        await socket.SendAsync(new ArraySegment<byte>(bytes), WebSocketMessageType.Text, true,
            CancellationToken.None);
    }

    /// <summary>
    /// Get the local IPv4 address of this machine.
    /// </summary>
    public static string GetLocalIPAddress()
    {
        try
        {
            // First priority: active adapter with a default IPv4 gateway (the router/Wi-Fi connection)
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                var props = ni.GetIPProperties();
                bool hasGateway = props.GatewayAddresses.Any(g =>
                    g.Address.AddressFamily == AddressFamily.InterNetwork &&
                    !IPAddress.IsLoopback(g.Address) &&
                    !g.Address.Equals(IPAddress.Any));

                if (hasGateway)
                {
                    foreach (var addr in props.UnicastAddresses)
                    {
                        if (addr.Address.AddressFamily == AddressFamily.InterNetwork &&
                            !IPAddress.IsLoopback(addr.Address))
                        {
                            return addr.Address.ToString();
                        }
                    }
                }
            }

            // Second priority: any non-virtual adapter with IPv4
            foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (ni.OperationalStatus != OperationalStatus.Up) continue;
                if (ni.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                string desc = ni.Description.ToLowerInvariant();
                string name = ni.Name.ToLowerInvariant();
                if (desc.Contains("virtual") || desc.Contains("hyper-v") || desc.Contains("vethernet") ||
                    desc.Contains("vmware") || desc.Contains("box") || desc.Contains("wsl") ||
                    name.Contains("virtual") || name.Contains("wsl"))
                    continue;

                var props = ni.GetIPProperties();
                foreach (var addr in props.UnicastAddresses)
                {
                    if (addr.Address.AddressFamily == AddressFamily.InterNetwork &&
                        !IPAddress.IsLoopback(addr.Address) &&
                        !addr.Address.ToString().StartsWith("169.254"))
                    {
                        return addr.Address.ToString();
                    }
                }
            }
        }
        catch { }

        return "127.0.0.1";
    }

    public IReadOnlyList<string> GetConnectedDeviceNames()
    {
        return _sessions.Values.Select(s => s.DeviceName).ToList().AsReadOnly();
    }

    private void Log(string message)
    {
        OnLog?.Invoke($"[{DateTime.Now:HH:mm:ss}] {message}");
    }

    public void Dispose()
    {
        _cts?.Cancel();
        _cts?.Dispose();
        try { _listener?.Stop(); } catch { }
    }
}

/// <summary>
/// Tracks per-session held input so that disconnecting one client
/// only releases keys/buttons that client was holding — not all clients' input.
/// </summary>
internal class SessionInputState
{
    public HashSet<ushort> HeldKeys { get; } = new();
    public HashSet<string> HeldMouseButtons { get; } = new();
}

internal class ClientSession
{
    public string SessionId { get; }
    public WebSocket Socket { get; }
    public string DeviceId { get; }
    public string DeviceName { get; }
    public DateTime ConnectedAt { get; } = DateTime.UtcNow;

    public ClientSession(string sessionId, WebSocket socket, string deviceId, string deviceName)
    {
        SessionId = sessionId;
        Socket = socket;
        DeviceId = deviceId;
        DeviceName = deviceName;
    }
}
