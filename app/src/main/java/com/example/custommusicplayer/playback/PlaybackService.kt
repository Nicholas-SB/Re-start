package com.example.custommusicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.custommusicplayer.data.db.MusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    // --- Listening stats ---

    // Database writes run on this scope. It's deliberately never cancelled, so the last
    // write still finishes while the service shuts down. (Each write takes milliseconds.)
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // `by lazy`: created the first time it's used (after onCreate), then reused.
    private val statsDao by lazy { MusicDatabase.getInstance(this).statsDao() }

    private val listeningTracker = ListeningTracker(
        recorder = object : ListeningRecorder {
            override fun recordPlay(songFileName: String, listenedMs: Long) {
                val playedAt = System.currentTimeMillis()
                statsScope.launch { statsDao.recordPlay(songFileName, playedAt, listenedMs) }
            }

            override fun recordSkip(songFileName: String, listenedMs: Long) {
                statsScope.launch { statsDao.recordSkip(songFileName, listenedMs) }
            }

            override fun recordListening(songFileName: String, listenedMs: Long) {
                statsScope.launch { statsDao.recordListening(songFileName, listenedMs) }
            }
        },
        // elapsedRealtime: milliseconds since boot. Unlike the wall clock, it never jumps
        // when the phone's time or time zone changes.
        clock = SystemClock::elapsedRealtime,
    )

    /** Feeds player events to the ListeningTracker. */
    private val statsListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // SEEK = Next/Previous/picked from the queue; PLAYLIST_CHANGED = started something else.
            val userMovedOn = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
            listeningTracker.songEnded(userSkipped = userMovedOn)
            listeningTracker.songStarted(mediaItem?.songFileName, mediaItem?.songDurationMs ?: 0L, player.isPlaying)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listeningTracker.playingChanged(isPlaying)
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
        player.addListener(statsListener)

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
        listeningTracker.songEnded(userSkipped = false)  // save the song that was playing
        player.removeListener(reshuffleListener)
        player.removeListener(statsListener)
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
