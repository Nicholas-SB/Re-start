package com.example.custommusicplayer.ui.device

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.custommusicplayer.R

/** How strongly the wheel vibrates. */
enum class VibrationLevel(val label: String) {
    OFF("Off"), LIGHT("Light"), NORMAL("Normal");

    fun next(): VibrationLevel = entries[(ordinal + 1) % entries.size]
}

/** How the Songs list is ordered. */
enum class SongSort(val label: String) {
    A_TO_Z("A–Z"), RECENTLY_ADDED("Recently added");

    fun next(): SongSort = entries[(ordinal + 1) % entries.size]
}

/**
 * The player's settings, saved in SharedPreferences (a small key-value file the app keeps)
 * so they survive closing the app.
 *
 * `by mutableStateOf` works in any class, not just in composables: whoever reads these
 * while drawing is redrawn when they change.
 */
class DeviceSettings(context: Context) {

    private val prefs = context.getSharedPreferences("device_settings", Context.MODE_PRIVATE)

    // `private set`: anyone can read these, only this class can change them (via the methods below).
    var clickSound by mutableStateOf(prefs.getBoolean(KEY_CLICK_SOUND, false))
        private set
    var vibration by mutableStateOf(loadVibration())
        private set
    var scratchSound by mutableStateOf(prefs.getBoolean(KEY_SCRATCH_SOUND, true))
        private set
    var pauseForCalls by mutableStateOf(prefs.getBoolean(KEY_PAUSE_FOR_CALLS, false))
        private set
    var songSort by mutableStateOf(
        SongSort.entries.firstOrNull { it.name == prefs.getString(KEY_SONG_SORT, null) } ?: SongSort.A_TO_Z
    )
        private set

    fun toggleClickSound() {
        clickSound = !clickSound
        prefs.edit().putBoolean(KEY_CLICK_SOUND, clickSound).apply()
    }

    fun cycleVibration() {
        vibration = vibration.next()
        prefs.edit().putString(KEY_VIBRATION_LEVEL, vibration.name).apply()
    }

    fun toggleScratchSound() {
        scratchSound = !scratchSound
        prefs.edit().putBoolean(KEY_SCRATCH_SOUND, scratchSound).apply()
    }

    fun changePauseForCalls(on: Boolean) {
        pauseForCalls = on
        prefs.edit().putBoolean(KEY_PAUSE_FOR_CALLS, on).apply()  // PlaybackService reads this
    }

    fun cycleSongSort() {
        songSort = songSort.next()
        prefs.edit().putString(KEY_SONG_SORT, songSort.name).apply()
    }

    private fun loadVibration(): VibrationLevel {
        prefs.getString(KEY_VIBRATION_LEVEL, null)?.let { saved ->
            return VibrationLevel.entries.firstOrNull { it.name == saved } ?: VibrationLevel.LIGHT
        }
        // Older versions saved a simple on/off switch: keep "off" if that's what was chosen.
        return if (prefs.getBoolean(KEY_OLD_VIBRATION, true)) VibrationLevel.LIGHT else VibrationLevel.OFF
    }

    private companion object {
        const val KEY_CLICK_SOUND = "click_sound"
        const val KEY_OLD_VIBRATION = "vibration"
        const val KEY_VIBRATION_LEVEL = "vibration_level"
        const val KEY_SCRATCH_SOUND = "scratch_sound"
        const val KEY_SONG_SORT = "song_sort"
        const val KEY_PAUSE_FOR_CALLS = "pause_for_calls"
    }
}

/**
 * What you feel and hear when using the wheel: a vibration tick and/or a soft click,
 * depending on the settings, and the DJ-scratch sound while scanning through a song.
 *
 * SoundPool is Android's player for short sound effects: it loads them into memory once,
 * so playing one is instant (unlike ExoPlayer, which is built for long tracks).
 */
class WheelFeedback(context: Context, private val settings: DeviceSettings) {

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            // USAGE_GAME plays on the *media* volume, the one your music uses. (Interface
            // "system sounds" are often muted, which made the clicker silent.) SoundPool never
            // takes audio focus, so the music keeps playing underneath.
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val tickSound = soundPool.load(context, R.raw.wheel_tick, 1)
    private val pressSound = soundPool.load(context, R.raw.button_press, 1)
    private val scratchSound = soundPool.load(context, R.raw.scratch_loop, 1)
    private var scratchStream = 0  // the playing scratch loop, 0 = none

    private val vibrator: Vibrator? = context.getSystemService(Vibrator::class.java)

    /** One step of the wheel. */
    fun tick(view: View) {
        vibrate(view, HapticFeedbackConstants.CLOCK_TICK, lightMs = 6)
        if (settings.clickSound) soundPool.play(tickSound, 0.5f, 0.5f, 1, 0, 1f)
    }

    /** A button press (ring buttons and the center). */
    fun press(view: View) {
        vibrate(view, HapticFeedbackConstants.KEYBOARD_TAP, lightMs = 10)
        if (settings.clickSound) soundPool.play(pressSound, 0.6f, 0.6f, 1, 0, 1f)
    }

    /** Holding a button long enough to do its "hold" action. */
    fun hold(view: View) = vibrate(view, HapticFeedbackConstants.LONG_PRESS, lightMs = 18)

    /** Starts the looping scratch sound (a bit higher when going forward, lower going back). */
    fun startScratch(forward: Boolean) {
        if (!settings.scratchSound || scratchStream != 0) return
        // play(sound, leftVolume, rightVolume, priority, loop = -1 forever, playback rate)
        scratchStream = soundPool.play(scratchSound, 0.8f, 0.8f, 2, -1, if (forward) 1.15f else 0.85f)
    }

    fun stopScratch() {
        if (scratchStream != 0) soundPool.stop(scratchStream)
        scratchStream = 0
    }

    /**
     * NORMAL uses Android's standard effect for this kind of touch. LIGHT uses a softer pulse
     * where the motor supports strength control, or else just a shorter one (on many phones,
     * shorter is the only way to feel lighter).
     */
    private fun vibrate(view: View, normalEffect: Int, lightMs: Long) {
        when (settings.vibration) {
            VibrationLevel.OFF -> {}
            VibrationLevel.NORMAL -> view.performHapticFeedback(normalEffect)
            VibrationLevel.LIGHT -> {
                val motor = vibrator ?: return
                val effect = if (motor.hasAmplitudeControl()) {
                    VibrationEffect.createOneShot(lightMs * 2, 70)  // amplitude 1..255
                } else {
                    VibrationEffect.createOneShot(lightMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // "Touch" vibrations follow the phone's touch-vibration setting.
                    motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
                } else {
                    @Suppress("DEPRECATION")
                    motor.vibrate(effect)
                }
            }
        }
    }

    /** Frees the sound memory; call when the screen goes away. */
    fun release() = soundPool.release()
}
