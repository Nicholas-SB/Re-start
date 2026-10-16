package com.example.custommusicplayer.playback

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
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
            // The permission may have been given since the service started, so check again.
            if (isPlaying) watchCalls()
        }
    }

    // --- Pausing for phone calls ---
    // Audio focus already pauses music for calls that *ring out loud*. A call on a silent
    // ringer asks nobody to pause, so if you allow it, we also watch the call state directly.

    private val telephony by lazy { getSystemService(TelephonyManager::class.java) }
    private var callWatcher: Any? = null  // the registered TelephonyCallback or PhoneStateListener
    private var pausedForCall = false

    private fun onCallStateChanged(state: Int) {
        val enabled = getSharedPreferences("device_settings", Context.MODE_PRIVATE).getBoolean("pause_for_calls", false)
        when (state) {
            TelephonyManager.CALL_STATE_RINGING, TelephonyManager.CALL_STATE_OFFHOOK ->
                if (enabled && player.isPlaying) {
                    player.pause()
                    pausedForCall = true
                }
            TelephonyManager.CALL_STATE_IDLE ->
                if (pausedForCall) {
                    pausedForCall = false
                    player.play()  // the call ended: carry on where we were
                }
        }
    }

    /** Starts watching calls, if the phone permission has been given (and we aren't already). */
    @SuppressLint("MissingPermission")  // checked just below
    private fun watchCalls() {
        if (callWatcher != null) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        callWatcher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // An object that extends a class *and* implements an interface: Kotlin's version of
            // an anonymous class like `new TelephonyCallback() implements CallStateListener`.
            object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = this@PlaybackService.onCallStateChanged(state)
            }.also { telephony.registerTelephonyCallback(mainExecutor, it) }
        } else {
            @Suppress("DEPRECATION")  // the pre-Android 12 way
            object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = this@PlaybackService.onCallStateChanged(state)
            }.also { telephony.listen(it, PhoneStateListener.LISTEN_CALL_STATE) }
        }
    }

    @Suppress("DEPRECATION")  // PhoneStateListener, for Android 11 and older
    private fun stopWatchingCalls() {
        val watcher = callWatcher ?: return
        // Check the Android version first: TelephonyCallback doesn't exist before Android 12.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && watcher is TelephonyCallback) {
            telephony.unregisterTelephonyCallback(watcher)
        } else if (watcher is PhoneStateListener) {
            telephony.listen(watcher, PhoneStateListener.LISTEN_NONE)
        }
        callWatcher = null
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
        watchCalls()
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
        stopWatchingCalls()
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
