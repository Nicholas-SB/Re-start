package com.example.custommusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.custommusicplayer.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the ExoPlayer. Living in a ViewModel means rotating the phone no longer
 * restarts the song. (In milestone 3 the player moves into a background service.)
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val player = ExoPlayer.Builder(application).build()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
    }

    // `init` runs when the object is constructed, like a Java constructor body.
    init {
        player.addListener(listener)
    }

    fun play(song: Song) {
        _currentSong.value = song
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
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

    // Called when the ViewModel is destroyed for good (e.g. the user closes the app).
    override fun onCleared() {
        player.removeListener(listener)
        player.release()
    }
}
