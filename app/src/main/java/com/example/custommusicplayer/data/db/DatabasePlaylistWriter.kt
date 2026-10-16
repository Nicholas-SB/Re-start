package com.example.custommusicplayer.data.db

import android.content.Context
import com.example.custommusicplayer.downloader.PlaylistWriter

/** Lets the downloader save imported playlists into the app's database. */
class DatabasePlaylistWriter(context: Context) : PlaylistWriter {

    private val dao = MusicDatabase.getInstance(context).playlistDao()

    override suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name))

    override suspend fun addSong(playlistId: Long, songFileName: String) {
        dao.addSong(playlistId, songFileName)  // skips songs already in the playlist
    }
}
