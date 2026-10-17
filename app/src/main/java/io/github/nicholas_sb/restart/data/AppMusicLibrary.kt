package io.github.nicholas_sb.restart.data

import android.content.Context
import io.github.nicholas_sb.restart.data.db.MusicDatabase
import io.github.nicholas_sb.restart.data.db.PlaylistEntity
import io.github.nicholas_sb.restart.downloader.LibrarySong
import io.github.nicholas_sb.restart.downloader.MusicLibrary

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
