package com.touchdeck.app.network

import android.util.Log
import com.touchdeck.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * WebSocket client communicating with TouchDeck Windows Companion.
 * Uses Challenge-Response HMAC-SHA256 authentication (zero plaintext token exposure)
 * and robust automatic reconnection.
 */
class TouchDeckClient {

    companion object {
        private const val TAG = "TouchDeckClient"
        private const val PING_INTERVAL_MS = 2000L
        private const val RECONNECT_DELAY_MS = 2500L
        private const val MAX_RECONNECT_ATTEMPTS = 5
    }

    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pingJob: Job? = null
    private var reconnectJob: Job? = null

    // State
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _serverName = MutableStateFlow("")
    val serverName: StateFlow<String> = _serverName.asStateFlow()

    private val _latencyMs = MutableStateFlow(-1L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private val _pairingCode = MutableStateFlow<String?>(null)
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Active connection parameters
    private var currentIp: String = ""
    private var currentPort: Int = 9876
    private var deviceName: String = ""
    private var deviceId: String = ""
    private var sessionToken: String? = null
    private var reconnectAttempts = 0
    private var userInitiatedDisconnect = false
    private var lastPingSent = 0L

    // Callbacks
    var onPaired: ((token: String, serverName: String) -> Unit)? = null
    var onAuthSuccess: (() -> Unit)? = null
    var onVibrationReceived: ((largeMotor: Int, smallMotor: Int) -> Unit)? = null

    fun connectForPairing(ip: String, port: Int, devId: String, devName: String) {
        connect(ip = ip, port = port, devName = devName, devId = devId, token = null)
    }

    fun connectWithToken(ip: String, port: Int, devId: String, token: String) {
        connect(ip = ip, port = port, devName = deviceName.ifBlank { "AirDesk" }, devId = devId, token = token)
    }

    fun connect(
        ip: String,
        port: Int = 9876,
        devName: String,
        devId: String,
        token: String? = null
    ) {
        currentIp = ip.trim()
        currentPort = port
        deviceName = devName
        deviceId = devId
        sessionToken = token
        reconnectAttempts = 0
        userInitiatedDisconnect = false

        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null

        connectInternal()
    }

    private fun connectInternal() {
        closeSocket()

        try {
            // Validate port range
            if (currentPort !in 1..65535) {
                _errorMessage.value = "Invalid port: $currentPort (must be 1–65535)"
                _connectionState.value = ConnectionState.FAILED
                return
            }

            // Validate IP/Host
            if (currentIp.isBlank()) {
                _errorMessage.value = "IP address cannot be empty"
                _connectionState.value = ConnectionState.FAILED
                return
            }

            okHttpClient = OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(8, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url("ws://$currentIp:$currentPort")
                .build()

            webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "WebSocket connected to $currentIp:$currentPort")

                    if (sessionToken == null) {
                        // Start pairing
                        _connectionState.value = ConnectionState.PAIRING
                        send(TouchDeckMessage.create(
                            MessageTypes.PAIR_REQUEST,
                            PairRequestPayload(
                                deviceName = deviceName,
                                deviceId = deviceId
                            )
                        ))
                    } else {
                        // For authenticated clients: wait for the server's auth_challenge
                        _connectionState.value = ConnectionState.AUTHENTICATING
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error: ${t.message}")
                    _errorMessage.value = "Connection failed: ${t.message}"
                    handleDisconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "WebSocket closed: $reason")
                    handleDisconnect()
                }
            })
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid connection URL: ${e.message}")
            _errorMessage.value = "Invalid server address: ${e.message}"
            _connectionState.value = ConnectionState.FAILED
        } catch (e: Exception) {
            Log.e(TAG, "Connection initiation failed: ${e.message}")
            _errorMessage.value = "Could not start connection: ${e.message}"
            handleDisconnect()
        }
    }

    private fun handleMessage(json: String) {
        val message = TouchDeckMessage.fromJson(json) ?: return

        when (message.type) {
            MessageTypes.AUTH_CHALLENGE -> {
                val payload = message.getPayload<AuthChallengePayload>() ?: return
                val token = sessionToken
                if (token != null) {
                    _connectionState.value = ConnectionState.AUTHENTICATING
                    // Compute HMAC-SHA256(token, nonce) — token is never sent in cleartext
                    val hmac = computeHmacSha256(token, payload.nonce)
                    send(TouchDeckMessage.create(
                        MessageTypes.AUTH,
                        AuthPayload(
                            deviceId = deviceId,
                            authResponse = hmac,
                            nonce = payload.nonce
                        )
                    ))
                    Log.d(TAG, "Sent HMAC challenge response for authentication")
                }
            }

            MessageTypes.PAIR_CHALLENGE -> {
                val payload = message.getPayload<PairChallengePayload>() ?: return
                _pairingCode.value = payload.code
                _connectionState.value = ConnectionState.PAIRING
            }

            MessageTypes.PAIR_RESULT -> {
                val payload = message.getPayload<PairResultPayload>() ?: return
                if (payload.success) {
                    sessionToken = payload.token
                    _serverName.value = payload.serverName
                    _connectionState.value = ConnectionState.CONNECTED
                    _pairingCode.value = null
                    reconnectAttempts = 0
                    startPing()
                    onPaired?.invoke(payload.token, payload.serverName)
                } else {
                    _errorMessage.value = payload.error ?: "Pairing failed"
                    _connectionState.value = ConnectionState.FAILED
                }
            }

            MessageTypes.AUTH_RESULT -> {
                val payload = message.getPayload<AuthResultPayload>() ?: return
                if (payload.success) {
                    _connectionState.value = ConnectionState.CONNECTED
                    reconnectAttempts = 0
                    startPing()
                    onAuthSuccess?.invoke()
                } else {
                    _errorMessage.value = payload.error ?: "Authentication failed"
                    _connectionState.value = ConnectionState.FAILED
                    sessionToken = null
                }
            }

            MessageTypes.PONG -> {
                val rtt = System.currentTimeMillis() - lastPingSent
                _latencyMs.value = rtt
            }

            MessageTypes.STATUS -> {
                val payload = message.getPayload<StatusPayload>()
                if (payload?.status == "error") {
                    _errorMessage.value = payload.message
                }
            }

            MessageTypes.VIBRATE -> {
                val payload = message.getPayload<VibrationPayload>() ?: return
                onVibrationReceived?.invoke(payload.largeMotor, payload.smallMotor)
            }
        }
    }

    private fun computeHmacSha256(key: String, data: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
            mac.init(secretKey)
            val bytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error computing HMAC: ${e.message}")
            ""
        }
    }

    /**
     * Confirm the pairing code displayed on the PC.
     */
    fun confirmPairing(code: String) {
        send(TouchDeckMessage.create(
            MessageTypes.PAIR_CONFIRM,
            PairConfirmPayload(code = code, deviceId = deviceId)
        ))
    }

    // --- Input Commands ---

    fun sendMouseMove(dx: Double, dy: Double) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.MOUSE_MOVE, MouseMovePayload(dx, dy)))
    }

    fun sendMouseButtonDown(button: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.MOUSE_BTN_DOWN, MouseButtonPayload(button)))
    }

    fun sendMouseButtonUp(button: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.MOUSE_BTN_UP, MouseButtonPayload(button)))
    }

    fun sendMouseScroll(dx: Double, dy: Double) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.MOUSE_SCROLL, MouseScrollPayload(dx, dy)))
    }

    fun sendKeyDown(keyCode: Int, code: String = "") {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.KEY_DOWN, KeyPayload(keyCode, code)))
    }

    fun sendKeyUp(keyCode: Int, code: String = "") {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.KEY_UP, KeyPayload(keyCode, code)))
    }

    fun sendTextInput(text: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.TEXT_INPUT, TextInputPayload(text)))
    }

    fun sendMediaAction(action: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.MEDIA_ACTION, MediaActionPayload(action)))
    }

    fun sendControllerButtonDown(button: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.CTRL_BTN_DOWN, ControllerButtonPayload(button)))
    }

    fun sendControllerButtonUp(button: String) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.CTRL_BTN_UP, ControllerButtonPayload(button)))
    }

    fun sendControllerStick(stick: String, x: Double, y: Double) {
        if (_connectionState.value != ConnectionState.CONNECTED) return
        send(TouchDeckMessage.create(MessageTypes.CTRL_STICK, ControllerStickPayload(stick, x, y)))
    }

    // --- Connection management ---

    private fun send(message: TouchDeckMessage) {
        webSocket?.send(message.toJson())
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                lastPingSent = System.currentTimeMillis()
                send(TouchDeckMessage.create(MessageTypes.PING))
                delay(PING_INTERVAL_MS)
            }
        }
    }

    private fun handleDisconnect() {
        pingJob?.cancel()
        val shouldReconnect = !userInitiatedDisconnect &&
                sessionToken != null &&
                reconnectAttempts < MAX_RECONNECT_ATTEMPTS

        if (shouldReconnect) {
            _connectionState.value = ConnectionState.RECONNECTING
            attemptReconnect()
        } else {
            if (_connectionState.value != ConnectionState.FAILED) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(RECONNECT_DELAY_MS)
            reconnectAttempts++
            Log.d(TAG, "Reconnect attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS to $currentIp:$currentPort")
            connectInternal()
        }
    }

    private fun closeSocket() {
        pingJob?.cancel()
        try {
            webSocket?.close(1000, "Closed")
        } catch (_: Exception) {}
        webSocket = null
    }

    fun disconnect(sendMessage: Boolean = true) {
        userInitiatedDisconnect = true
        pingJob?.cancel()
        reconnectJob?.cancel()
        reconnectAttempts = 0

        if (sendMessage) {
            try {
                send(TouchDeckMessage.create(MessageTypes.DISCONNECT))
            } catch (_: Exception) {}
        }

        try {
            webSocket?.close(1000, "User disconnect")
        } catch (_: Exception) {}

        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _latencyMs.value = -1
        _pairingCode.value = null
    }

    fun destroy() {
        disconnect()
        scope.cancel()
        okHttpClient?.dispatcher?.executorService?.shutdown()
    }
}
