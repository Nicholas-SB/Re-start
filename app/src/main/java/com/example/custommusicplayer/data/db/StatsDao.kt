package com.example.custommusicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Reads and writes listening stats. Writes come from PlaybackService, reads from the UI. */
@Dao
interface StatsDao {

    @Query("SELECT * FROM song_stats")
    fun observeAllStats(): Flow<List<SongStatsEntity>>

    /**
     * Songs played within [windowMs] of any play of [songFileName], listed once per such play.
     * A "self join": the table is read twice, as `seed` and `other`, and the rows are paired up.
     */
    @Query(
        """
        SELECT other.songFileName FROM play_events seed
        JOIN play_events other ON other.playedAt BETWEEN seed.playedAt - :windowMs AND seed.playedAt + :windowMs
        WHERE seed.songFileName = :songFileName AND other.songFileName != :songFileName
        """
    )
    suspend fun songsPlayedAround(songFileName: String, windowMs: Long): List<String>

    // IGNORE: if the song already has a row, keep it (we only want to make sure one exists).
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(stats: SongStatsEntity)

    @Insert
    suspend fun insertPlayEvent(event: PlayEventEntity)

    @Query(
        """
        UPDATE song_stats SET playCount = playCount + 1, lastPlayedAt = :playedAt, listenedMs = listenedMs + :listenedMs
        WHERE songFileName = :songFileName
        """
    )
    suspend fun incrementPlay(songFileName: String, playedAt: Long, listenedMs: Long)

    @Query("UPDATE song_stats SET skipCount = skipCount + 1, listenedMs = listenedMs + :listenedMs WHERE songFileName = :songFileName")
    suspend fun incrementSkip(songFileName: String, listenedMs: Long)

    @Query("UPDATE song_stats SET listenedMs = listenedMs + :listenedMs WHERE songFileName = :songFileName")
    suspend fun addListeningTime(songFileName: String, listenedMs: Long)

    // Each of these makes sure the row exists, then updates it, all-or-nothing.

    @Transaction
    suspend fun recordPlay(songFileName: String, playedAt: Long, listenedMs: Long) {
        insertIfMissing(SongStatsEntity(songFileName))
        incrementPlay(songFileName, playedAt, listenedMs)
        insertPlayEvent(PlayEventEntity(songFileName = songFileName, playedAt = playedAt))
    }

    @Transaction
    suspend fun recordSkip(songFileName: String, listenedMs: Long) {
        insertIfMissing(SongStatsEntity(songFileName))
        incrementSkip(songFileName, listenedMs)
    }

    @Transaction
    suspend fun recordListening(songFileName: String, listenedMs: Long) {
        insertIfMissing(SongStatsEntity(songFileName))
        addListeningTime(songFileName, listenedMs)
    }
}
