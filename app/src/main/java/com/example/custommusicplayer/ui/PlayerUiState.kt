package com.example.custommusicplayer.ui

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
) {
    /** Where the current song is in the play order, counting from 1 (for "2 of 10"). */
    val queuePosition: Int get() = queueSize - upNext.size
}

/** One song in the queue. [queueIndex] is its position in the player's list, used to jump to it. */
data class QueueEntry(val queueIndex: Int, val songId: Long)

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
