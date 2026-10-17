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
    val songFileName: String,  // for streamed songs: "stream:<YouTube id>"
    val playedAt: Long,
    // Only for streamed songs, which have no file to look the name up from. (Added in v4.)
    val title: String? = null,
    val artist: String? = null,
)

/** Minutes listened on one day (in ms), plays and skips alike. For "hours listened" per month. */
@Entity(tableName = "listening_days")
data class ListeningDayEntity(
    @PrimaryKey val day: Long,  // days since 1970, in the phone's time zone
    val listenedMs: Long,
)

/** Plays per month ("2026-10"), for the History page. */
data class MonthPlays(
    val month: String,
    val plays: Int,
)
