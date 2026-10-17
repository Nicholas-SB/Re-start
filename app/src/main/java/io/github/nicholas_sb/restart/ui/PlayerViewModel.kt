package io.github.nicholas_sb.restart.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import androidx.media3.common.Timeline
import android.os.Looper
import android.os.Handler
import android.os.Bundle
import io.github.nicholas_sb.restart.playback.SleepTimerCommand
import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.playback.PlaybackService
import io.github.nicholas_sb.restart.playback.toMediaItem
import io.github.nicholas_sb.restart.playback.toStreamedSong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * The UI's remote control for PlaybackService.
 *
 * A MediaController implements the same Player interface as ExoPlayer, so calling
 * play()/pause() looks the same as before. The difference is that the commands are sent
 * to the player inside the service, which keeps playing when the app is closed.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Kept separate from uiState because it changes several times a second while playing.
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    // Null until the connection to the service is ready.
    private var controller: MediaController? = null

    // The sleep timer, as PlaybackService reports it (null = off).
    private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
    val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

    // A skip sets off a burst of player events (new song, buffering, ready, playing, ...).
    // Rebuilding the state for each one rebuilt the screen 6-8 times in a row and made the
    // disc stutter, so the burst is gathered into one update that runs just after it.
    private val mainHandler = Handler(Looper.getMainLooper())
    private var updatePending = false
    private var queueChanged = true  // only then does Up Next need rebuilding (it's the slow part)
    private var updateJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Only when songs were added, removed or moved. The other reason ("source update")
            // comes with every song that starts, when its length becomes known.
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) queueChanged = true
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                queueChanged = true
            }
            if (updatePending) return
            updatePending = true
            mainHandler.postDelayed({
                updatePending = false
                updateState(player)
            }, GATHER_EVENTS_MS)
        }
    }

    // Connecting to the service is asynchronous. buildAsync() returns a "future"
    // (like Java's Future) and we get a callback once the controller is ready.
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    )
        // The service sends news that isn't about the player itself (here: the sleep timer)
        // as "session extras"; this listener hears when they change.
        .setListener(object : MediaController.Listener {
            override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                _sleepTimer.value = sleepTimerFrom(extras)
            }
        })
        .buildAsync()

    init {
        controllerFuture.addListener(
            {
                val connected = controllerFuture.get()
                connected.addListener(listener)
                controller = connected
                // If music was already playing (app reopened), show it right away.
                updateState(connected)
                _sleepTimer.value = sleepTimerFrom(connected.sessionExtras)
            },
            ContextCompat.getMainExecutor(application),  // run the callback on the UI thread
        )

        // The player doesn't send an event every time the position moves, so we ask for it,
        // but only when it matters: while music is playing AND the UI is watching the
        // position. collectAsStateWithLifecycle() stops watching when the app isn't visible
        // (in the background, screen off), so then nothing ticks and the CPU can sleep.
        // Seeks and pauses still update the position through onEvents -> updateState.
        viewModelScope.launch {
            combine(_positionMs.subscriptionCount, _uiState) { watchers, state -> watchers > 0 && state.isPlaying }
                .distinctUntilChanged()
                // collectLatest cancels the previous loop as soon as the answer changes.
                .collectLatest { shouldTick ->
                    while (shouldTick) {
                        controller?.let { _positionMs.value = it.currentPosition }
                        delay(200)  // often enough for lyrics to switch lines on time
                    }
                }
        }
    }

    private fun updateState(player: Player) {
        // Read everything from the player now (it may only be asked on this thread)...
        val state = PlayerUiState(
            currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = player.isPlaying,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            upNext = _uiState.value.upNext,
            queueSize = player.mediaItemCount,
            currentStreamedSong = player.currentMediaItem?.toStreamedSong(),
        )
        _positionMs.value = player.currentPosition

        // ...but walk the queue for Up Next on a background thread: with a long queue that
        // takes several milliseconds, enough to make the screen miss a frame. A Timeline is
        // read-only, so the other thread can safely read it. If an update is still on its way,
        // this one replaces it (and redoes its Up Next, if it had one to do).
        val rebuildUpNext = queueChanged || updateJob?.isActive == true
        queueChanged = false
        updateJob?.cancel()
        if (!rebuildUpNext) {
            _uiState.value = state
            return
        }
        val timeline = player.currentTimeline
        val currentIndex = player.currentMediaItemIndex
        val shuffle = player.shuffleModeEnabled
        updateJob = viewModelScope.launch {
            val upNext = withContext(Dispatchers.Default) { buildUpNext(timeline, currentIndex, shuffle) }
            _uiState.value = state.copy(upNext = upNext)
        }
    }

    /**
     * Lists the songs after the current one in the order they'll actually play.
     * The player's "timeline" knows the next index, taking shuffle into account.
     */
    private fun buildUpNext(timeline: Timeline, currentIndex: Int, shuffle: Boolean): List<QueueEntry> {
        if (timeline.isEmpty) return emptyList()

        val upNext = mutableListOf<QueueEntry>()
        val window = Timeline.Window()  // reused for each song, filled in by getWindow
        var index = timeline.getNextWindowIndex(currentIndex, Player.REPEAT_MODE_OFF, shuffle)
        while (index != C.INDEX_UNSET) {
            val item = timeline.getWindow(index, window).mediaItem
            val songId = item.mediaId.toLongOrNull()
            // Only streamed songs (negative ids) need their details read back from the item.
            if (songId != null) upNext += QueueEntry(index, songId, if (songId < 0) item.toStreamedSong() else null)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffle)
        }
        return upNext
    }

    /** Replaces the queue with [songs] and starts playing the one at [startIndex]. */
    fun playQueue(songs: List<Song>, startIndex: Int) {
        // `?: return`: if we aren't connected yet, ignore the tap.
        val player = controller ?: return
        player.setMediaItems(songs.map { it.toMediaItem() }, startIndex, /* startPositionMs = */ 0)
        player.prepare()
        player.play()
    }

    /** Turns shuffle off and plays [songs] in this order (a song mix is already in its order). */
    fun playInOrder(songs: List<Song>) {
        if (songs.isEmpty()) return
        controller?.shuffleModeEnabled = false
        playQueue(songs, 0)
    }

    /**
     * Adds [songs] after a mix's first song, if that song is still the only one in the queue
     * (if you started something else while the mix was loading, it's left alone).
     */
    fun continueMix(first: Song, songs: List<Song>): Boolean {
        val player = controller ?: return false
        if (player.mediaItemCount != 1 || player.getMediaItemAt(0).mediaId != first.id.toString()) return false
        player.addMediaItems(songs.map { it.toMediaItem() })
        return true
    }

    /** Stops the music in [minutes] (0 = turn the timer off, SleepTimerCommand.END_OF_SONG = after this song). */
    fun setSleepTimer(minutes: Int) {
        controller?.sendCustomCommand(SleepTimerCommand.command, Bundle().apply { putInt(SleepTimerCommand.KEY_MINUTES, minutes) })
    }

    private fun sleepTimerFrom(extras: Bundle): SleepTimer? {
        if (!extras.containsKey(SleepTimerCommand.KEY_STOPS_AT)) return null
        val stopsAt = extras.getLong(SleepTimerCommand.KEY_STOPS_AT)
        return if (stopsAt == SleepTimerCommand.END_OF_SONG.toLong()) SleepTimer.EndOfSong else SleepTimer.At(stopsAt)
    }

    /** Takes every copy of a song out of the queue (it was deleted). Playing moves on to the next one. */
    fun removeFromQueue(songId: Long) {
        val player = controller ?: return
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId == songId.toString()) player.removeMediaItem(index)
        }
    }

    /**
     * Swaps a queued song for [song] (a mix's guessed title turned out to be this). The song
     * that's playing right now is left alone, so it doesn't restart.
     */
    fun replaceInQueue(oldSongId: Long, song: Song) {
        val player = controller ?: return
        for (index in 0 until player.mediaItemCount) {
            if (index != player.currentMediaItemIndex && player.getMediaItemAt(index).mediaId == oldSongId.toString()) {
                player.replaceMediaItem(index, song.toMediaItem())
            }
        }
    }

    /** Turns shuffle on and plays [songs] starting from a random one. */
    fun shuffleAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        controller?.shuffleModeEnabled = true
        playQueue(songs, Random.nextInt(songs.size))
    }

    fun togglePlayPause() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            // The queue finished: start the current song over instead of doing nothing.
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            else -> player.play()
        }
    }

    fun skipToNext() {
        controller?.seekToNext()
    }

    /** Like most players: restarts the song if you're a few seconds in, otherwise goes back. */
    fun skipToPrevious() {
        controller?.seekToPrevious()
    }

    /** Jumps to a song in the queue (from the "Up next" list). */
    fun skipToQueueItem(queueIndex: Int) {
        controller?.seekTo(queueIndex, 0)
    }

    /** Jumps forward (+) or back (-) within the song, for holding ⏭ / ⏮. */
    fun seekBy(deltaMs: Long) {
        val player = controller ?: return
        val duration = player.duration
        // Stop just short of the end, so scanning forward doesn't skip to the next song.
        val latest = if (duration == C.TIME_UNSET) Long.MAX_VALUE else (duration - 1_000).coerceAtLeast(0)
        seekTo((player.currentPosition + deltaMs).coerceIn(0, latest))
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun toggleShuffle() {
        // Kotlin turns getShuffleModeEnabled()/setShuffleModeEnabled() into a property.
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val player = controller ?: return
        player.repeatMode = when (_uiState.value.repeatMode.next()) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    // Disconnect from the service. This does NOT stop the music; the service keeps playing.
    override fun onCleared() {
        mainHandler.removeCallbacksAndMessages(null)  // a gathered update still waiting
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}

/** How long to wait for the rest of a burst of player events before updating the screen. */
private const val GATHER_EVENTS_MS = 30L
