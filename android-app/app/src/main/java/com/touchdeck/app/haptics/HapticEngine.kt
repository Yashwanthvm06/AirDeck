package com.touchdeck.app.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Manages haptic feedback patterns for various input events.
 * Respects device capabilities and user preferences.
 */
class HapticEngine(private val context: Context) {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vm.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    var isEnabled: Boolean = true
    var intensity: Float = 0.6f  // 0.0 to 1.0
    var keyboardHapticsEnabled: Boolean = true
    var controllerHapticsEnabled: Boolean = true

    val hasVibrator: Boolean get() = vibrator.hasVibrator()

    /**
     * Short click feedback - used for button presses.
     */
    fun clickFeedback() {
        if (!isEnabled) return
        vibrate(duration = 20, amplitude = scaleAmplitude(80))
    }

    fun buttonClick() = clickFeedback()
    fun mouseClick() = clickFeedback()

    /**
     * Keyboard key press feedback - lighter than button click.
     */
    fun keyPressFeedback() {
        if (!isEnabled || !keyboardHapticsEnabled) return
        vibrate(duration = 10, amplitude = scaleAmplitude(50))
    }

    fun keyboardKey() = keyPressFeedback()

    /**
     * Controller button feedback - medium intensity.
     */
    fun controllerButtonFeedback() {
        if (!isEnabled || !controllerHapticsEnabled) return
        vibrate(duration = 25, amplitude = scaleAmplitude(100))
    }

    fun controllerButton() = controllerButtonFeedback()

    /**
     * Confirmation feedback - double pulse for success.
     */
    fun confirmationFeedback() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 30, 60, 30)
            val amplitudes = intArrayOf(0, scaleAmplitude(120), 0, scaleAmplitude(160))
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 30, 60, 30), -1)
        }
    }

    /**
     * Connection established feedback.
     */
    fun connectionEstablished() {
        if (!isEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 50, 100, 50, 100, 80)
            val amplitudes = intArrayOf(0, scaleAmplitude(80), 0, scaleAmplitude(120), 0, scaleAmplitude(180))
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 100, 50, 100, 80), -1)
        }
    }

    /**
     * Connection lost feedback - single long pulse.
     */
    fun connectionLost() {
        if (!isEnabled) return
        vibrate(duration = 200, amplitude = scaleAmplitude(200))
    }

    /**
     * Strong feedback for important actions.
     */
    fun strongFeedback() {
        if (!isEnabled) return
        vibrate(duration = 50, amplitude = scaleAmplitude(200))
    }

    /**
     * Game rumble/vibration feedback forwarded from the PC via ViGEmBus.
     * Receives Xbox controller motor intensities (0-255) and converts to phone vibration.
     * Large motor = low-frequency heavy rumble, Small motor = high-frequency light buzz.
     */
    fun gameRumble(largeMotor: Int, smallMotor: Int) {
        if (!isEnabled || !controllerHapticsEnabled) return

        val combinedIntensity = maxOf(largeMotor, smallMotor)
        if (combinedIntensity == 0) {
            // Game stopped rumble — cancel any ongoing vibration
            vibrator.cancel()
            return
        }

        val amplitude = scaleAmplitude(combinedIntensity)

        // Large motor produces longer, heavier rumble; small motor is a quick buzz
        val duration = when {
            largeMotor > 150 && smallMotor > 150 -> 150L  // Both strong → intense combo
            largeMotor > smallMotor -> 120L                // Heavy rumble (explosion, impact)
            smallMotor > largeMotor -> 60L                 // Light buzz (gunfire, engine)
            else -> 80L                                     // Balanced
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && largeMotor > 0 && smallMotor > 0) {
            // Dual-motor simulation: two pulses mimicking large + small motor
            val largeAmp = scaleAmplitude(largeMotor)
            val smallAmp = scaleAmplitude(smallMotor)
            val timings = longArrayOf(0, 80, 20, 40)
            val amplitudes = intArrayOf(0, largeAmp, 0, smallAmp)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            vibrate(duration = duration, amplitude = amplitude)
        }
    }

    /**
     * Test a specific vibration pattern.
     */
    fun testPattern(patternName: String) {
        when (patternName) {
            "click" -> clickFeedback()
            "key_press" -> keyPressFeedback()
            "controller" -> controllerButtonFeedback()
            "confirmation" -> confirmationFeedback()
            "connection" -> connectionEstablished()
            "disconnect" -> connectionLost()
            "strong" -> strongFeedback()
            "rumble" -> gameRumble(200, 100)  // Test game rumble pattern
        }
    }

    /**
     * Use View-based haptic feedback when available.
     */
    fun performViewHaptic(view: View, feedbackConstant: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
        if (!isEnabled) return
        view.performHapticFeedback(feedbackConstant)
    }

    private fun vibrate(duration: Long, amplitude: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(duration, amplitude.coerceIn(1, 255))
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    private fun scaleAmplitude(base: Int): Int {
        return (base * intensity).toInt().coerceIn(1, 255)
    }
}
