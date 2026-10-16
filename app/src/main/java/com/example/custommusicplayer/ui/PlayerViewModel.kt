package com.example.custommusicplayer.ui

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.playback.PlaybackService
import com.example.custommusicplayer.playback.toMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The UI's remote control for PlaybackService.
 *
 * A MediaController implements the same Player interface as ExoPlayer, so calling
 * play()/pause() looks the same as before. The difference is that the commands are sent
 * to the player inside the service, which keeps playing when the app is closed.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Id of the song that's loaded in the player (playing or paused), or null.
    private val _currentSongId = MutableStateFlow<Long?>(null)
    val currentSongId: StateFlow<Long?> = _currentSongId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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
    }

    private fun updateState(player: Player) {
        _isPlaying.value = player.isPlaying
        _currentSongId.value = player.currentMediaItem?.mediaId?.toLongOrNull()
    }

    fun play(song: Song) {
        // `?: return`: if we aren't connected yet, ignore the tap.
        val player = controller ?: return
        player.setMediaItem(song.toMediaItem())
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = controller ?: return
        when {
            player.isPlaying -> player.pause()
            // The song finished: start it over instead of doing nothing.
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0)
                player.play()
            }
            else -> player.play()
        }
    }

    // Disconnect from the service. This does NOT stop the music; the service keeps playing.
    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}
