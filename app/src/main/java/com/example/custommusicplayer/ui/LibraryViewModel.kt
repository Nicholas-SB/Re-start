package com.example.custommusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.custommusicplayer.data.AlbumArtLoader
import com.example.custommusicplayer.data.MusicScanner
import com.example.custommusicplayer.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the song list. A ViewModel survives screen rotation (the Activity doesn't),
 * so we don't rescan every time the phone turns.
 *
 * AndroidViewModel gives us the Application context, which is safe to keep around
 * (unlike an Activity, which would leak after rotation).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MusicScanner(application)
    val artLoader = AlbumArtLoader(application)

    // Pattern: a private *Mutable* flow we write to, exposed publicly as read-only.
    // A StateFlow is an observable value: the UI is notified whenever it changes.
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var hasLoaded = false

    /** Scans the first time it's called; later calls do nothing. */
    fun loadSongsOnce() {
        if (hasLoaded) return
        hasLoaded = true
        rescan()
    }

    fun rescan() {
        // viewModelScope.launch starts a coroutine that is cancelled automatically
        // if the ViewModel is destroyed.
        viewModelScope.launch {
            _isScanning.value = true
            _songs.value = scanner.scan()
            _isScanning.value = false
        }
    }
}
