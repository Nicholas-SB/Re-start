package io.github.nicholas_sb.restart.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nicholas_sb.restart.data.AlbumArtLoader
import io.github.nicholas_sb.restart.data.MusicScanner
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.lyrics.LyricsLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A folder you can pick as your music folder, and how many songs are in it. */
data class FolderChoice(val folder: String, val songCount: Int)

/**
 * Holds the song list. A ViewModel survives screen rotation (the Activity doesn't),
 * so we don't rescan every time the phone turns.
 *
 * AndroidViewModel gives us the Application context, which is safe to keep around
 * (unlike an Activity, which would leak after rotation).
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val scanner = MusicScanner(application)
    // Helpers that load extra things for a song on demand.
    val artLoader = AlbumArtLoader(application)
    val lyricsLoader = LyricsLoader(application)

    // The chosen music folder, saved so it survives closing the app.
    private val _musicFolder = MutableStateFlow(MusicScanner.chosenFolder(application))
    val musicFolder: StateFlow<String> = _musicFolder.asStateFlow()

    // Pattern: a private *Mutable* flow we write to, exposed publicly as read-only.
    // A StateFlow is an observable value: the UI is notified whenever it changes.
    // Every audio file Android knows about, in any folder.
    private val allSongs = MutableStateFlow<List<Song>>(emptyList())

    /** The library: songs in the chosen folder, plus the downloads folder. */
    val songs: StateFlow<List<Song>> = combine(allSongs, _musicFolder) { all, folder ->
        all.filter { MusicScanner.isInLibrary(it, folder) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Top-level folders that contain audio (biggest first), to pick from in Settings. */
    val folderChoices: StateFlow<List<FolderChoice>> = allSongs.map { all ->
        val counts = all.groupingBy { it.folder.substringBefore('/') }.eachCount()
        (counts + (MusicScanner.DEFAULT_FOLDER to (counts[MusicScanner.DEFAULT_FOLDER] ?: 0)))  // always offer Music
            .filterKeys { it.isNotEmpty() }
            .map { (folder, count) -> FolderChoice(folder, count) }
            .sortedByDescending { it.songCount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            // Show what Android already knows right away...
            allSongs.value = scanner.querySongs()
            // ...then have it index any new files in our folders, and refresh the list.
            scanner.indexNewFiles(listOf(_musicFolder.value, MusicScanner.DOWNLOADS_FOLDER))
            allSongs.value = scanner.querySongs()
            _isScanning.value = false
        }
    }

    /** Switch the library to another folder (e.g. "YMusic"). */
    fun setMusicFolder(folder: String) {
        _musicFolder.value = folder
        MusicScanner.saveChosenFolder(getApplication(), folder)
        rescan()
    }
}
