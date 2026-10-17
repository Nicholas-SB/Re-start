package io.github.nicholas_sb.restart.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Reads every playlist and stat for a backup, and swaps them all for a restored one. */
@Dao
interface BackupDao {

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    suspend fun playlistSongs(): List<PlaylistSongEntity>

    @Query("SELECT * FROM song_stats")
    suspend fun stats(): List<SongStatsEntity>

    @Query("SELECT * FROM play_events ORDER BY playedAt")
    suspend fun playEvents(): List<PlayEventEntity>

    @Query("SELECT * FROM listening_days ORDER BY day")
    suspend fun listeningDays(): List<ListeningDayEntity>

    @Query("DELETE FROM playlists")  // their songs go too (ON DELETE CASCADE)
    suspend fun deletePlaylists()

    @Query("DELETE FROM song_stats")
    suspend fun deleteStats()

    @Query("DELETE FROM play_events")
    suspend fun deletePlayEvents()

    @Query("DELETE FROM listening_days")
    suspend fun deleteListeningDays()

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertPlaylistSongs(songs: List<PlaylistSongEntity>)

    @Insert
    suspend fun insertStats(stats: List<SongStatsEntity>)

    @Insert
    suspend fun insertPlayEvents(events: List<PlayEventEntity>)

    @Insert
    suspend fun insertListeningDays(days: List<ListeningDayEntity>)

    /**
     * Replaces all playlists and stats. @Transaction makes it all-or-nothing: if anything
     * fails halfway, the old data is still there.
     */
    @Transaction
    suspend fun replaceAll(
        playlists: List<Pair<PlaylistEntity, List<String>>>,  // each playlist with its songs' file names
        stats: List<SongStatsEntity>,
        playEvents: List<PlayEventEntity>,
        listeningDays: List<ListeningDayEntity>,
    ) {
        deletePlaylists()
        deleteStats()
        deletePlayEvents()
        deleteListeningDays()
        for ((playlist, songs) in playlists) {
            val id = insertPlaylist(playlist.copy(id = 0))  // new ids; the order is what matters
            insertPlaylistSongs(songs.mapIndexed { position, fileName -> PlaylistSongEntity(playlistId = id, songFileName = fileName, position = position) })
        }
        insertStats(stats)
        insertPlayEvents(playEvents.map { it.copy(id = 0) })
        insertListeningDays(listeningDays)
    }
}
