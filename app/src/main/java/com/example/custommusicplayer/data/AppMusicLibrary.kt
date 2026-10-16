package com.example.custommusicplayer.data

import android.content.Context
import com.example.custommusicplayer.data.db.MusicDatabase
import com.example.custommusicplayer.data.db.PlaylistEntity
import com.example.custommusicplayer.downloader.LibrarySong
import com.example.custommusicplayer.downloader.MusicLibrary

/** Gives the downloader's playlist import our songs (to skip ones you have) and our playlists. */
class AppMusicLibrary(private val context: Context) : MusicLibrary {

    private val scanner = MusicScanner(context)
    private val dao = MusicDatabase.getInstance(context).playlistDao()

    override suspend fun songs(): List<LibrarySong> {
        val folder = MusicScanner.chosenFolder(context)
        return scanner.querySongs()
            .filter { MusicScanner.isInLibrary(it, folder) }
            .map { LibrarySong(it.fileName, it.title, it.artist, it.durationMs) }
    }

    override suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun addSong(playlistId: Long, songFileName: String): Boolean = dao.addSong(playlistId, songFileName)
}
