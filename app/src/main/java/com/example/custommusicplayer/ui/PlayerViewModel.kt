package com.example.custommusicplayer.ui

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.playback.PlaybackService
import com.example.custommusicplayer.playback.toMediaItem
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

    private val listener = object : Player.Listener {
        // One callback for every kind of change; we just re-read what we show.
        override fun onEvents(player: Player, events: Player.Events) {
            updateState(player)
        }
    }

    // Connecting to the service is asynchronous. buildAsync() returns a "future"
    // (like Java's Future) and we get a callback once the controller is ready.
    private val controllerFuture = MediaController.Builder(
        application,
        SessionToken(application, ComponentName(application, PlaybackService::class.java)),
    ).buildAsync()

    init {
        controllerFuture.addListener(
            {
                val connected = controllerFuture.get()
                connected.addListener(listener)
                controller = connected
                // If music was already playing (app reopened), show it right away.
                updateState(connected)
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
        _uiState.value = PlayerUiState(
            currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = player.isPlaying,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            upNext = buildUpNext(player),
            queueSize = player.mediaItemCount,
        )
        _positionMs.value = player.currentPosition
    }

    /**
     * Lists the songs after the current one in the order they'll actually play.
     * The player's "timeline" knows the next index, taking shuffle into account.
     */
    private fun buildUpNext(player: Player): List<QueueEntry> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()

        val upNext = mutableListOf<QueueEntry>()
        var index = timeline.getNextWindowIndex(player.currentMediaItemIndex, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
        while (index != C.INDEX_UNSET) {
            val songId = player.getMediaItemAt(index).mediaId.toLongOrNull()
            if (songId != null) upNext += QueueEntry(index, songId)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
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
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}
