package com.touchdeck.app.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

/**
 * TouchDeck protocol message envelope.
 * All communication uses this format.
 */
data class TouchDeckMessage(
    @SerializedName("v") val version: Int = 1,
    @SerializedName("t") val type: String,
    @SerializedName("seq") val sequence: Long = 0,
    @SerializedName("ts") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("p") val payload: JsonElement? = null
) {
    companion object {
        private val gson = Gson()

        fun create(type: String, payload: Any? = null): TouchDeckMessage {
            val payloadElement = if (payload != null) {
                gson.toJsonTree(payload)
            } else null

            return TouchDeckMessage(
                type = type,
                payload = payloadElement
            )
        }

        fun fromJson(json: String): TouchDeckMessage? {
            return try {
                gson.fromJson(json, TouchDeckMessage::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun toJson(): String = gson.toJson(this)

    inline fun <reified T> getPayload(): T? {
        return try {
            payload?.let { Gson().fromJson(it, T::class.java) }
        } catch (e: Exception) {
            null
        }
    }
}

// --- Payload models ---

data class PairRequestPayload(
    @SerializedName("device_name") val deviceName: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("app_version") val appVersion: String = "1.0.0"
)

data class PairChallengePayload(
    @SerializedName("code") val code: String
)

data class PairConfirmPayload(
    @SerializedName("code") val code: String,
    @SerializedName("device_id") val deviceId: String
)

data class PairResultPayload(
    @SerializedName("success") val success: Boolean,
    @SerializedName("token") val token: String = "",
    @SerializedName("server_name") val serverName: String = "",
    @SerializedName("error") val error: String? = null
)

data class AuthChallengePayload(
    @SerializedName("nonce") val nonce: String = ""
)

data class AuthPayload(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("token") val token: String? = null,
    @SerializedName("auth_response") val authResponse: String? = null,
    @SerializedName("nonce") val nonce: String? = null
)

data class AuthResultPayload(
    @SerializedName("success") val success: Boolean,
    @SerializedName("error") val error: String? = null
)

data class MouseMovePayload(
    @SerializedName("dx") val dx: Double,
    @SerializedName("dy") val dy: Double
)

data class MouseButtonPayload(
    @SerializedName("btn") val button: String
)

data class MouseScrollPayload(
    @SerializedName("dx") val dx: Double,
    @SerializedName("dy") val dy: Double
)

data class KeyPayload(
    @SerializedName("key") val key: Int,
    @SerializedName("code") val code: String = ""
)

data class TextInputPayload(
    @SerializedName("text") val text: String
)

data class MediaActionPayload(
    @SerializedName("action") val action: String
)

data class ControllerButtonPayload(
    @SerializedName("btn") val button: String
)

data class VibrationPayload(
    @SerializedName("large_motor") val largeMotor: Int,
    @SerializedName("small_motor") val smallMotor: Int
)

data class ControllerStickPayload(
    @SerializedName("stick") val stick: String = "left",
    @SerializedName("x") val x: Double,
    @SerializedName("y") val y: Double
)

data class StatusPayload(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String? = null
)

/** Protocol message type constants */
object MessageTypes {
    const val PAIR_REQUEST = "pair_request"
    const val PAIR_CHALLENGE = "pair_challenge"
    const val PAIR_CONFIRM = "pair_confirm"
    const val PAIR_RESULT = "pair_result"
    const val AUTH_CHALLENGE = "auth_challenge"
    const val AUTH = "auth"
    const val AUTH_RESULT = "auth_result"
    const val MOUSE_MOVE = "mouse_move"
    const val MOUSE_BTN_DOWN = "mouse_btn_down"
    const val MOUSE_BTN_UP = "mouse_btn_up"
    const val MOUSE_SCROLL = "mouse_scroll"
    const val KEY_DOWN = "key_down"
    const val KEY_UP = "key_up"
    const val TEXT_INPUT = "text_input"
    const val MEDIA_ACTION = "media_action"
    const val CTRL_BTN_DOWN = "ctrl_btn_down"
    const val CTRL_BTN_UP = "ctrl_btn_up"
    const val CTRL_STICK = "ctrl_stick"
    const val PING = "ping"
    const val PONG = "pong"
    const val STATUS = "status"
    const val DISCONNECT = "disconnect"
    const val VIBRATE = "vibrate"
}
