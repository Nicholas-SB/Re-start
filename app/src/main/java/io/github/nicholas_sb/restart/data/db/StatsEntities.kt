package io.github.nicholas_sb.restart.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lifetime listening stats for one song. Keyed by file name, like playlists, so the stats
 * survive Android re-indexing the file.
 */
@Entity(tableName = "song_stats")
data class SongStatsEntity(
    @PrimaryKey val songFileName: String,
    val playCount: Int = 0,
    val skipCount: Int = 0,
    val lastPlayedAt: Long? = null,  // epoch millis of the last counted play, null if never
    val listenedMs: Long = 0,        // total time actually spent listening
)

/** One counted play, with when it happened. Lets a song mix ask "what do I play around this song?". */
@Entity(
    tableName = "play_events",
    indices = [Index("playedAt")],  // "plays since X" queries filter on this column
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songFileName: String,
    val playedAt: Long,
)
