package io.github.nicholas_sb.restart.playback

import java.time.LocalDate
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.nicholas_sb.restart.data.STREAM_SCHEME
import io.github.nicholas_sb.restart.data.db.MusicDatabase
import io.github.nicholas_sb.restart.downloader.YoutubeMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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

    // --- Streaming (songs in a mix that aren't on the phone) ---

    private val youtube by lazy { YoutubeMusic(this) }

    /**
     * Turns "restart-stream://<id>" into the song's real audio link, right when ExoPlayer is
     * about to load it (it does this on its own loading thread, so waiting here is fine).
     */
    @OptIn(UnstableApi::class)
    private fun streamingDataSource() = ResolvingDataSource.Factory(DefaultDataSource.Factory(this)) { dataSpec ->
        if (dataSpec.uri.scheme == STREAM_SCHEME) {
            val videoId = dataSpec.uri.host.orEmpty()
            // runBlocking: wait for the suspend function here, on this (background) thread.
            dataSpec.withUri(Uri.parse(runBlocking { youtube.audioUrl(videoId) }))
        } else {
            dataSpec  // a file on the phone: nothing to do
        }
    }

    /** A streamed song that can't play (no internet, removed from YouTube): skip to the next. */
    private val skipBrokenStreams = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val current = player.currentMediaItem ?: return
            if (current.localConfiguration?.uri?.scheme == STREAM_SCHEME && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    // --- Battery saver (audio offload) ---
    // Normally the main processor decodes the music and keeps waking up to do it. With
    // "offload", the phone's low-power audio chip decodes it instead, so with the screen off
    // the processor can sleep. Not every phone does it well, so it's a switch in Extras.

    private val settingsPrefs by lazy { getSharedPreferences("device_settings", Context.MODE_PRIVATE) }

    // Kept in a field: SharedPreferences only holds a weak reference to its listeners.
    private val batterySaverSwitch = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_AUDIO_OFFLOAD) applyBatterySaver()
    }

    @OptIn(UnstableApi::class)
    private fun applyBatterySaver() {
        val enabled = settingsPrefs.getBoolean(KEY_AUDIO_OFFLOAD, true)
        val offload = TrackSelectionParameters.AudioOffloadPreferences.Builder()
            .setAudioOffloadMode(
                if (enabled) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            )
            // Don't insist on gapless: more phones can offload, and our songs are mostly singles.
            .setIsGaplessSupportRequired(false)
            .build()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(offload)
            .build()
    }

    /** Writes to the log when the audio chip takes over or gives back, to check it works. */
    @OptIn(UnstableApi::class)
    private val offloadLogger = object : ExoPlayer.AudioOffloadListener {
        override fun onOffloadedPlayback(offloadedPlayback: Boolean) {
            Log.i(TAG, "Audio offload ${if (offloadedPlayback) "on" else "off"}")
        }

        // While offloaded, the player itself goes to sleep between the chip's requests for more audio.
        override fun onSleepingForOffloadChanged(sleepingForOffload: Boolean) {
            Log.i(TAG, "Sleeping while the audio chip plays: $sleepingForOffload")
        }
    }

    // --- Sleep timer ---
    // It lives here rather than in the screen, so it keeps counting with the app swiped away.

    private val sleepScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepJob: Job? = null

    /** [minutes] until the music stops; 0 turns the timer off; END_OF_SONG stops after this song. */
    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        player.volume = 1f  // in case a fade-out was cut short
        // ExoPlayer can pause by itself when a song ends.
        player.pauseAtEndOfMediaItems = minutes == SleepTimerCommand.END_OF_SONG
        when {
            minutes == SleepTimerCommand.END_OF_SONG -> announceSleepTimer(SleepTimerCommand.END_OF_SONG.toLong())
            minutes > 0 -> {
                announceSleepTimer(System.currentTimeMillis() + minutes * 60_000L)
                sleepJob = sleepScope.launch {
                    delay(minutes * 60_000L - SLEEP_FADE_MS)
                    fadeOutAndPause()
                    announceSleepTimer(null)
                }
            }
            else -> announceSleepTimer(null)
        }
    }

    /** Lowers the volume over a few seconds, pauses, then puts the volume back for next time. */
    private suspend fun fadeOutAndPause() {
        val steps = 30
        for (step in 1..steps) {
            player.volume = 1f - step.toFloat() / steps
            delay(SLEEP_FADE_MS / steps)
        }
        player.pause()
        player.volume = 1f
    }

    /** Tells every connected controller (our screen) when the music will stop; null = timer off. */
    private fun announceSleepTimer(stopsAt: Long?) {
        mediaSession?.setSessionExtras(Bundle().apply { if (stopsAt != null) putLong(SleepTimerCommand.KEY_STOPS_AT, stopsAt) })
    }

    /** "End of song": once the player has paused there, the timer is done. */
    private val sleepAtEndOfSong = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && player.pauseAtEndOfMediaItems) {
                player.pauseAtEndOfMediaItems = false
                announceSleepTimer(null)
            }
        }
    }

    /** Lets our own app send the sleep timer command; other apps (Bluetooth, ...) get the usual controls. */
    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SleepTimerCommand.command)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction != SleepTimerCommand.ACTION) {
                return super.onCustomCommand(session, controller, customCommand, args)
            }
            setSleepTimer(args.getInt(SleepTimerCommand.KEY_MINUTES))
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    // --- Listening stats ---

    // Database writes run on this scope. It's deliberately never cancelled, so the last
    // write still finishes while the service shuts down. (Each write takes milliseconds.)
    private val statsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // `by lazy`: created the first time it's used (after onCreate), then reused.
    private val statsDao by lazy { MusicDatabase.getInstance(this).statsDao() }

    // Names of the streamed songs played lately, by stats key: a play is saved after the next
    // song has started, so the name can't be read from the player any more by then.
    private val streamedNames = mutableMapOf<String, Pair<String?, String?>>()

    private fun today(): Long = LocalDate.now().toEpochDay()

    private val listeningTracker = ListeningTracker(
        recorder = object : ListeningRecorder {
            override fun recordPlay(songFileName: String, listenedMs: Long) {
                val playedAt = System.currentTimeMillis()
                val (title, artist) = streamedNames[songFileName] ?: (null to null)
                statsScope.launch { statsDao.recordPlay(songFileName, playedAt, listenedMs, today(), title, artist) }
            }

            override fun recordSkip(songFileName: String, listenedMs: Long) {
                statsScope.launch { statsDao.recordSkip(songFileName, listenedMs, today()) }
            }

            override fun recordListening(songFileName: String, listenedMs: Long) {
                statsScope.launch { statsDao.recordListening(songFileName, listenedMs, today()) }
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
            val key = mediaItem?.statsKey
            if (key != null && key.startsWith(STREAM_KEY_PREFIX)) {
                if (streamedNames.size > 50) streamedNames.clear()  // only recent ones are needed
                streamedNames[key] = mediaItem.mediaMetadata.title?.toString() to mediaItem.mediaMetadata.artist?.toString()
            }
            listeningTracker.songStarted(key, mediaItem?.songDurationMs ?: 0L, player.isPlaying)
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

    @OptIn(UnstableApi::class)  // DefaultMediaSourceFactory with our own data source
    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streamingDataSource()))
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
        player.addListener(skipBrokenStreams)
        player.addListener(sleepAtEndOfSong)
        player.addAudioOffloadListener(offloadLogger)
        applyBatterySaver()
        settingsPrefs.registerOnSharedPreferenceChangeListener(batterySaverSwitch)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .setCallback(sessionCallback)
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
        sleepScope.cancel()
        settingsPrefs.unregisterOnSharedPreferenceChangeListener(batterySaverSwitch)
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

private const val TAG = "PlaybackService"

/** Same key as DeviceSettings' "Battery saver" switch. */
private const val KEY_AUDIO_OFFLOAD = "audio_offload"

/** How long the music fades out before the sleep timer pauses it. */
private const val SLEEP_FADE_MS = 8_000L
