package com.example.custommusicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO = "data access object": the list of database operations the app can do.
 *
 * You only write the SQL (or nothing, for @Insert/@Update); Room generates the implementing
 * class at compile time and checks the SQL against the tables, so typos are build errors.
 *
 * Functions returning Flow are "live": Room re-runs the query and emits a new result
 * whenever the tables it reads change. `suspend` functions run once, off the main thread.
 */
@Dao
interface PlaylistDao {

    @Query(
        """
        SELECT p.id, p.name, COUNT(s.id) AS songCount
        FROM playlists p LEFT JOIN playlist_songs s ON s.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    fun observeEntries(playlistId: Long): Flow<List<PlaylistSongEntity>>

    /** Returns the new playlist's id. */
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getEntries(playlistId: Long): List<PlaylistSongEntity>

    @Insert
    suspend fun insertEntry(entry: PlaylistSongEntity)

    @Update
    suspend fun updateEntries(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int

    // Functions with a body combine several operations. @Transaction makes them all-or-nothing,
    // so the playlist can't end up half-updated if something fails midway.

    /** Adds a song at the end of the playlist. */
    @Transaction
    suspend fun addSong(playlistId: Long, songFileName: String) {
        insertEntry(
            PlaylistSongEntity(playlistId = playlistId, songFileName = songFileName, position = lastPosition(playlistId) + 1)
        )
    }

    /** Moves the song at list position [from] to position [to], shifting the others. */
    @Transaction
    suspend fun moveEntry(playlistId: Long, from: Int, to: Int) {
        val entries = getEntries(playlistId).toMutableList()
        if (from !in entries.indices || to !in entries.indices) return
        entries.add(to, entries.removeAt(from))
        // Renumber everything 0, 1, 2, ... in the new order.
        updateEntries(entries.mapIndexed { index, entry -> entry.copy(position = index) })
    }
}
