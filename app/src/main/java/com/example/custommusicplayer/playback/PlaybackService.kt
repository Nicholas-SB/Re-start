package com.example.custommusicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Plays the music, independently of the UI.
 *
 * A Service is an Android component with no screen that can keep running after the user
 * leaves the app. MediaSessionService (from Media3) adds a "media session": a standard way
 * for the notification, lock screen, headphone buttons, Bluetooth, etc. to see what's
 * playing and control it. Media3 also creates and updates the media notification for us.
 *
 * The app's UI doesn't touch the ExoPlayer directly anymore. It connects with a
 * MediaController, just like the notification and lock screen do (see PlayerViewModel).
 */
class PlaybackService : MediaSessionService() {

    // `?` because it only exists between onCreate() and onDestroy().
    private var mediaSession: MediaSession? = null

    // `lateinit`: assigned in onCreate() rather than in the constructor, but never null after.
    private lateinit var player: ExoPlayer

    /**
     * Makes a fresh Fisher-Yates shuffle (current song first) whenever shuffle is turned on
     * or a new queue starts playing while shuffle is on.
     */
    private val reshuffleListener = object : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) reshuffle()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled) {
                reshuffle()
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun reshuffle() {
        player.setShuffleOrder(FisherYatesShuffleOrder(player.mediaItemCount, player.currentMediaItemIndex))
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            // Tell Android this is music, and let ExoPlayer manage "audio focus":
            // it pauses for phone calls, ducks or pauses when another app plays audio,
            // and resumes after a short interruption (like a call) ends.
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged or Bluetooth disconnects.
            .setHandleAudioBecomingNoisy(true)
            // Keep the CPU awake while playing with the screen off.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(reshuffleListener)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    /** Called when a controller (our UI, the notification, ...) wants to connect. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** The user swiped the app away from Recents: keep playing music, but quit if paused. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        player.removeListener(reshuffleListener)
        player.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    /** What happens when the notification is tapped: bring the app to the front. */
    private fun openAppIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
