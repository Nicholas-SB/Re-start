package com.example.custommusicplayer.ui.device

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.custommusicplayer.R

/**
 * The player's settings, saved in SharedPreferences (a small key-value file the app keeps)
 * so they survive closing the app.
 *
 * `by mutableStateOf` works in any class, not just in composables: whoever reads
 * clickSound / vibration while drawing is redrawn when they change.
 */
class DeviceSettings(context: Context) {

    private val prefs = context.getSharedPreferences("device_settings", Context.MODE_PRIVATE)

    // `private set`: anyone can read these, only this class can change them (via the setters below).
    var clickSound by mutableStateOf(prefs.getBoolean(KEY_CLICK_SOUND, false))
        private set
    var vibration by mutableStateOf(prefs.getBoolean(KEY_VIBRATION, true))
        private set

    fun toggleClickSound() {
        clickSound = !clickSound
        prefs.edit().putBoolean(KEY_CLICK_SOUND, clickSound).apply()
    }

    fun toggleVibration() {
        vibration = !vibration
        prefs.edit().putBoolean(KEY_VIBRATION, vibration).apply()
    }

    private companion object {
        const val KEY_CLICK_SOUND = "click_sound"
        const val KEY_VIBRATION = "vibration"
    }
}

/**
 * What you feel and hear when using the wheel: a haptic tick and/or a soft click,
 * depending on the settings.
 *
 * SoundPool is Android's player for short sound effects: it loads them into memory once,
 * so playing one is instant (unlike ExoPlayer, which is built for long tracks).
 */
class WheelFeedback(context: Context, private val settings: DeviceSettings) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            // "Sonification" = interface sounds. It doesn't take audio focus, so music keeps playing.
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val tickSound = soundPool.load(context, R.raw.wheel_tick, 1)
    private val pressSound = soundPool.load(context, R.raw.button_press, 1)

    /** One step of the wheel. */
    fun tick(view: View) = feedback(view, HapticFeedbackConstants.CLOCK_TICK, tickSound, volume = 0.35f)

    /** A button press (ring buttons and the center). */
    fun press(view: View) = feedback(view, HapticFeedbackConstants.KEYBOARD_TAP, pressSound, volume = 0.45f)

    /** Holding a button long enough to do its "hold" action. */
    fun hold(view: View) = feedback(view, HapticFeedbackConstants.LONG_PRESS, pressSound, volume = 0.45f)

    private fun feedback(view: View, haptic: Int, sound: Int, volume: Float) {
        if (settings.vibration) view.performHapticFeedback(haptic)
        if (settings.clickSound) soundPool.play(sound, volume, volume, 1, 0, 1f)
    }

    /** Frees the sound memory; call when the screen goes away. */
    fun release() = soundPool.release()
}
