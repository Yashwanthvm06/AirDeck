package com.touchdeck.app.model

/**
 * Connection state machine for the Android client.
 */
enum class ConnectionState {
    DISCONNECTED,
    DISCOVERING,
    CONNECTING,
    PAIRING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

/**
 * Represents a paired PC stored in preferences.
 */
data class PairedServer(
    val serverName: String,
    val ipAddress: String,
    val port: Int,
    val token: String,
    val pairedAt: Long = System.currentTimeMillis()
)

/**
 * Controller button mapping configuration.
 */
data class ControllerMapping(
    val button: String,
    val keyCode: Int,
    val keyName: String
) {
    companion object {
        val DEFAULTS = listOf(
            ControllerMapping("a", 0x20, "Space"),
            ControllerMapping("b", 0x1B, "Escape"),
            ControllerMapping("x", 0x45, "E"),
            ControllerMapping("y", 0x52, "R"),
            ControllerMapping("lb", 0x51, "Q"),
            ControllerMapping("rb", 0x46, "F"),
            ControllerMapping("lt", 0x10, "Shift"),
            ControllerMapping("rt", 0x11, "Ctrl"),
            ControllerMapping("start", 0x0D, "Enter"),
            ControllerMapping("select", 0x09, "Tab"),
            ControllerMapping("dpad_up", 0x26, "Up"),
            ControllerMapping("dpad_down", 0x28, "Down"),
            ControllerMapping("dpad_left", 0x25, "Left"),
            ControllerMapping("dpad_right", 0x27, "Right")
        )
    }
}

/**
 * Application-wide settings.
 */
data class AppSettings(
    val hapticEnabled: Boolean = true,
    val hapticIntensity: Float = 0.6f,
    val hapticOnKeyboard: Boolean = true,
    val hapticOnController: Boolean = true,
    val cursorSensitivity: Float = 1.5f,
    val scrollSpeed: Float = 1.0f,
    val tapToClick: Boolean = true,
    val faceButtonScale: Float = 1.0f,
    val accentColor: Long = 0xFF00D4FF,
    val controllerMappings: List<ControllerMapping> = ControllerMapping.DEFAULTS
)
