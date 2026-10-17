package io.github.nicholas_sb.restart.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/**
 * How the app sets the sleep timer in PlaybackService, and how the service says when the
 * music will stop. (A "custom command" is Media3's way to send our own requests to the
 * player, next to the standard ones like play and pause.)
 */
object SleepTimerCommand {
    const val ACTION = "io.github.nicholas_sb.restart.SLEEP_TIMER"

    /** In the command: minutes until the music stops, 0 = off, or [END_OF_SONG]. */
    const val KEY_MINUTES = "minutes"

    /** In the session's extras: when the music stops (epoch millis), or [END_OF_SONG]. Missing = off. */
    const val KEY_STOPS_AT = "sleepTimerStopsAt"

    const val END_OF_SONG = -1

    val command = SessionCommand(ACTION, Bundle.EMPTY)
}
