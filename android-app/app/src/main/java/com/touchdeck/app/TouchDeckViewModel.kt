package com.touchdeck.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.touchdeck.app.haptics.HapticEngine
import com.touchdeck.app.model.*
import com.touchdeck.app.network.TouchDeckClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Main ViewModel managing connection state, settings, and input dispatch.
 */
class TouchDeckViewModel(application: Application) : AndroidViewModel(application) {

    val client = TouchDeckClient()
    val haptics = HapticEngine(application)

    // Regular preferences for non-sensitive settings
    private val prefs = application.getSharedPreferences("touchdeck_prefs", Context.MODE_PRIVATE)

    // Encrypted preferences for sensitive data (session tokens, device credentials)
    // Fail-closed: Never fall back to unencrypted plaintext storage if hardware keystore fails
    private val securePrefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(application)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            application,
            "touchdeck_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.e("TouchDeckVM", "Hardware-backed keystore unavailable. Tokens will be kept in-memory only (fail-closed against plaintext disk storage).", e)
        null
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _pairedServer = MutableStateFlow(loadPairedServer())
    val pairedServer: StateFlow<PairedServer?> = _pairedServer.asStateFlow()

    // Device identity
    val deviceId: String = getDeviceId(application)
    val deviceName: String = Settings.Global.getString(
        application.contentResolver, Settings.Global.DEVICE_NAME
    ) ?: android.os.Build.MODEL

    init {
        // Migrate token from plain prefs to encrypted prefs (one-time migration)
        migrateTokenToSecurePrefs()

        // Set up pairing callback
        client.onPaired = { token, serverName ->
            viewModelScope.launch {
                savePairedServer(PairedServer(
                    serverName = serverName,
                    ipAddress = _lastConnectIp.value,
                    port = _lastConnectPort.value,
                    token = token
                ))
                haptics.connectionEstablished()
            }
        }

        client.onAuthSuccess = {
            viewModelScope.launch {
                haptics.connectionEstablished()
            }
        }

        // Forward game rumble/vibration from PC to phone haptics
        client.onVibrationReceived = { largeMotor, smallMotor ->
            haptics.gameRumble(largeMotor, smallMotor)
        }
    }

    private val _lastConnectIp = MutableStateFlow(prefs.getString("last_ip", "") ?: "")
    val lastConnectIp: StateFlow<String> = _lastConnectIp.asStateFlow()

    private val _lastConnectPort = MutableStateFlow(prefs.getInt("last_port", 9876))
    val lastConnectPort: StateFlow<Int> = _lastConnectPort.asStateFlow()

    fun connectForPairing(ip: String, port: Int) {
        _lastConnectIp.value = ip
        _lastConnectPort.value = port
        prefs.edit().putString("last_ip", ip).putInt("last_port", port).apply()
        client.connectForPairing(ip, port, deviceId, deviceName)
    }

    fun connectWithSavedServer() {
        val server = _pairedServer.value ?: return
        _lastConnectIp.value = server.ipAddress
        _lastConnectPort.value = server.port
        client.connectWithToken(server.ipAddress, server.port, deviceId, server.token)
    }

    fun confirmPairing(code: String) {
        client.confirmPairing(code)
    }

    fun disconnect() {
        client.disconnect()
        haptics.connectionLost()
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        haptics.isEnabled = newSettings.hapticEnabled
        haptics.intensity = newSettings.hapticIntensity
        haptics.keyboardHapticsEnabled = newSettings.hapticOnKeyboard
        haptics.controllerHapticsEnabled = newSettings.hapticOnController
        saveSettings(newSettings)
    }

    fun forgetPairedServer() {
        disconnect() // Immediately terminate any active session with this server
        // Clear from both regular and secure preferences
        prefs.edit()
            .remove("server_name")
            .remove("server_ip")
            .remove("server_port")
            .remove("server_token")
            .remove("server_paired_at")
            .apply()
        securePrefs?.edit()
            ?.remove("server_token")
            ?.remove("server_name")
            ?.remove("server_ip")
            ?.remove("server_port")
            ?.remove("server_paired_at")
            ?.apply()
        _pairedServer.value = null
    }

    // --- One-time migration: move token from plain prefs to encrypted prefs ---

    private fun migrateTokenToSecurePrefs() {
        val plainToken = prefs.getString("server_token", null)
        if (plainToken != null && securePrefs != null) {
            // Token exists in plain prefs — migrate to encrypted
            securePrefs.edit()
                .putString("server_token", plainToken)
                .putString("server_name", prefs.getString("server_name", ""))
                .putString("server_ip", prefs.getString("server_ip", ""))
                .putInt("server_port", prefs.getInt("server_port", 9876))
                .putLong("server_paired_at", prefs.getLong("server_paired_at", 0))
                .apply()
            // Remove from plain prefs
            prefs.edit()
                .remove("server_token")
                .remove("server_name")
                .remove("server_ip")
                .remove("server_port")
                .remove("server_paired_at")
                .apply()
            android.util.Log.i("TouchDeckVM", "Migrated session token to encrypted storage")
        }
    }

    // --- Settings persistence (non-sensitive, stays in regular prefs) ---

    private fun loadSettings(): AppSettings {
        return AppSettings(
            hapticEnabled = prefs.getBoolean("haptic_enabled", true),
            hapticIntensity = prefs.getFloat("haptic_intensity", 0.6f),
            hapticOnKeyboard = prefs.getBoolean("haptic_keyboard", true),
            hapticOnController = prefs.getBoolean("haptic_controller", true),
            cursorSensitivity = prefs.getFloat("cursor_sensitivity", 1.5f),
            scrollSpeed = prefs.getFloat("scroll_speed", 1.0f),
            tapToClick = prefs.getBoolean("tap_to_click", true),
            faceButtonScale = prefs.getFloat("face_button_scale", 1.0f)
        )
    }

    private fun saveSettings(s: AppSettings) {
        prefs.edit()
            .putBoolean("haptic_enabled", s.hapticEnabled)
            .putFloat("haptic_intensity", s.hapticIntensity)
            .putBoolean("haptic_keyboard", s.hapticOnKeyboard)
            .putBoolean("haptic_controller", s.hapticOnController)
            .putFloat("cursor_sensitivity", s.cursorSensitivity)
            .putFloat("scroll_speed", s.scrollSpeed)
            .putBoolean("tap_to_click", s.tapToClick)
            .putFloat("face_button_scale", s.faceButtonScale)
            .apply()
    }

    // --- Server credentials persistence (sensitive, uses encrypted prefs) ---

    private fun savePairedServer(server: PairedServer) {
        securePrefs?.edit()
            ?.putString("server_name", server.serverName)
            ?.putString("server_ip", server.ipAddress)
            ?.putInt("server_port", server.port)
            ?.putString("server_token", server.token)
            ?.putLong("server_paired_at", server.pairedAt)
            ?.apply()
        _pairedServer.value = server
    }

    private fun loadPairedServer(): PairedServer? {
        val sp = securePrefs ?: return null
        val name = sp.getString("server_name", null) ?: return null
        val ip = sp.getString("server_ip", null) ?: return null
        val token = sp.getString("server_token", null) ?: return null
        return PairedServer(
            serverName = name,
            ipAddress = ip,
            port = sp.getInt("server_port", 9876),
            token = token,
            pairedAt = sp.getLong("server_paired_at", 0)
        )
    }

    private fun getDeviceId(context: Context): String {
        var id = prefs.getString("device_id", null)
        if (id == null) {
            id = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    override fun onCleared() {
        super.onCleared()
        client.destroy()
    }
}
