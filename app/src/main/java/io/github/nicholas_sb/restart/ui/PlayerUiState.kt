package io.github.nicholas_sb.restart.ui

import io.github.nicholas_sb.restart.data.Song

/**
 * Everything the UI needs to know about playback, in one immutable object.
 * When anything changes, the ViewModel publishes a new PlayerUiState.
 *
 * Default values (`= null`, `= false`) mean you can write PlayerUiState() for "nothing playing".
 */
data class PlayerUiState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val upNext: List<QueueEntry> = emptyList(),  // songs after the current one, in play order
    val queueSize: Int = 0,
    val currentStreamedSong: Song? = null,  // when the current song is streamed (not in the library)
) {
    /** Where the current song is in the play order, counting from 1 (for "2 of 10"). */
    val queuePosition: Int get() = queueSize - upNext.size
}

/** A sleep timer that's on: stops at a time, or at the end of the current song. */
sealed interface SleepTimer {
    data class At(val stopsAtMs: Long) : SleepTimer
    data object EndOfSong : SleepTimer
}

/**
 * One song in the queue. [queueIndex] is its position in the player's list, used to jump to it.
 * [streamedSong] is set for streamed songs, which can't be looked up in the library by id.
 */
data class QueueEntry(val queueIndex: Int, val songId: Long, val streamedSong: Song? = null)

/**
 * An `enum class` works like a Java enum. We use our own instead of Media3's
 * Player.REPEAT_MODE_* Int constants, so `when` can check we handled every case.
 */
enum class RepeatMode {
    OFF, ALL, ONE;

    /** The mode the repeat button switches to next: off -> all -> one -> off. */
    fun next(): RepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }
}
