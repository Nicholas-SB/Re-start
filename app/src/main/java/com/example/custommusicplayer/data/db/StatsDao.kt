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

    @Query(
        """
        SELECT songFileName, COUNT(*) AS plays FROM play_events
        WHERE playedAt >= :since
        GROUP BY songFileName
        """
    )
    fun observePlayCountsSince(since: Long): Flow<List<RecentPlayCount>>

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
