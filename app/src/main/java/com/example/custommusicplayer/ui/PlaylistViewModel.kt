package com.example.custommusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.custommusicplayer.data.Song
import com.example.custommusicplayer.data.db.MusicDatabase
import com.example.custommusicplayer.data.db.PlaylistEntity
import com.example.custommusicplayer.data.db.PlaylistSongEntity
import com.example.custommusicplayer.data.db.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A playlist plus its entries, in order. */
data class PlaylistDetail(
    val playlist: PlaylistEntity,
    val entries: List<PlaylistSongEntity>,
)

/** Creates, edits and reads playlists. All the data lives in the Room database. */
class PlaylistViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MusicDatabase.getInstance(application).playlistDao()

    // stateIn turns the database Flow into a StateFlow the UI can read at any time.
    // WhileSubscribed(5000): keep the query running for 5 s after the UI stops watching
    // (so a screen rotation doesn't restart it).
    val playlists: StateFlow<List<PlaylistSummary>> = dao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live view of one playlist; emits null if it gets deleted. */
    fun observePlaylist(playlistId: Long): Flow<PlaylistDetail?> =
        // combine: whenever either flow emits, build a new value from the latest of both.
        combine(dao.observePlaylist(playlistId), dao.observeEntries(playlistId)) { playlist, entries ->
            playlist?.let { PlaylistDetail(it, entries) }
        }

    /** Which playlists already have [song] in them. */
    fun observePlaylistsContaining(song: Song): Flow<Set<Long>> =
        dao.observePlaylistsContaining(song.fileName).map { it.toSet() }

    fun createPlaylist(name: String, firstSong: Song? = null) {
        viewModelScope.launch {
            val playlistId = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
            if (firstSong != null) dao.addSong(playlistId, firstSong.fileName)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { dao.renamePlaylist(playlistId, name.trim()) }
    }

    fun setPinned(playlistId: Long, pinned: Boolean) {
        viewModelScope.launch { dao.setPinned(playlistId, pinned) }
    }

    /** The playlist's songs (their file names), in order. */
    suspend fun songFileNames(playlistId: Long): List<String> = dao.getEntries(playlistId).map { it.songFileName }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { dao.deletePlaylist(playlistId) }
    }

    fun addSong(playlistId: Long, song: Song) {
        viewModelScope.launch { dao.addSong(playlistId, song.fileName) }
    }

    fun removeEntry(entry: PlaylistSongEntity) {
        viewModelScope.launch { dao.deleteEntry(entry.id) }
    }

    fun moveEntry(playlistId: Long, from: Int, to: Int) {
        viewModelScope.launch { dao.moveEntry(playlistId, from, to) }
    }
}
