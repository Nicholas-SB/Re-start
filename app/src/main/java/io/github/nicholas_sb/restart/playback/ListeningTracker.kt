package io.github.nicholas_sb.restart.playback

/** Where ListeningTracker sends what it decided. PlaybackService saves these to the database. */
interface ListeningRecorder {
    fun recordPlay(songFileName: String, listenedMs: Long)
    fun recordSkip(songFileName: String, listenedMs: Long)
    fun recordListening(songFileName: String, listenedMs: Long)
}

/**
 * Watches playback and decides what counts as a play or a skip.
 *
 * - A **play**: you listened to half the song, or 4 minutes, whichever comes first
 *   (the rule Last.fm uses). Only real listening counts; seeking ahead doesn't.
 * - A **skip**: you moved on (Next, picked another song) before it counted as a play.
 * - Listening time is added to the song's total either way.
 *
 * It knows nothing about ExoPlayer or the database, so it can be unit-tested with a fake
 * clock. [clock] returns milliseconds and only needs to go forward (not the time of day).
 */
class ListeningTracker(
    private val recorder: ListeningRecorder,
    private val clock: () -> Long,
) {
    private var songFileName: String? = null
    private var songDurationMs = 0L
    private var playingSince: Long? = null  // when the current stretch of playing began, or null if paused
    private var listenedTotalMs = 0L        // all listening to this song, for the "counts as a play" rule
    private var unsavedMs = 0L              // listening not written to the recorder yet
    private var countedAsPlay = false

    /** A new song became the current one. */
    fun songStarted(fileName: String?, durationMs: Long, isPlaying: Boolean) {
        songFileName = fileName
        songDurationMs = durationMs
        listenedTotalMs = 0
        unsavedMs = 0
        countedAsPlay = false
        playingSince = if (isPlaying) clock() else null
    }

    fun playingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (playingSince == null) playingSince = clock()
        } else {
            stopClock()
            // Save on every pause, so nothing is lost if the app is closed afterwards.
            save(userSkipped = false)
        }
    }

    /** The current song is done. [userSkipped]: the user moved on instead of letting it finish. */
    fun songEnded(userSkipped: Boolean) {
        stopClock()
        save(userSkipped)
        songFileName = null
    }

    private fun stopClock() {
        val since = playingSince ?: return
        val stretch = clock() - since
        listenedTotalMs += stretch
        unsavedMs += stretch
        playingSince = null
    }

    private fun save(userSkipped: Boolean) {
        val fileName = songFileName ?: return
        when {
            !countedAsPlay && listenedTotalMs >= requiredForPlayMs() -> {
                countedAsPlay = true
                recorder.recordPlay(fileName, unsavedMs)
            }
            !countedAsPlay && userSkipped && listenedTotalMs > 0 -> recorder.recordSkip(fileName, unsavedMs)
            unsavedMs > 0 -> recorder.recordListening(fileName, unsavedMs)
            else -> return
        }
        unsavedMs = 0
    }

    private fun requiredForPlayMs(): Long = when {
        songDurationMs <= 0 -> MIN_LISTEN_MS  // length unknown
        else -> minOf(songDurationMs / 2, MAX_REQUIRED_MS)
    }

    companion object {
        private const val MIN_LISTEN_MS = 30_000L
        private const val MAX_REQUIRED_MS = 4 * 60_000L
    }
}
