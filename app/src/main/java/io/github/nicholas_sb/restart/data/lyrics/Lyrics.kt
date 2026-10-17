package io.github.nicholas_sb.restart.data.lyrics

/**
 * A song's lyrics: either synced (each line has a time) or plain text.
 *
 * A `sealed interface` is a closed set of types: every Lyrics is exactly one of the classes
 * declared here. `when (lyrics)` can then check all cases without needing an `else`.
 */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

/** One line of synced lyrics, shown from [timeMs] until the next line starts. */
data class LyricLine(val timeMs: Long, val text: String)

/** Where the lyrics came from, shown under them so you know which source won. */
enum class LyricsSource { LRC_FILE, EMBEDDED }

data class LoadedLyrics(val lyrics: Lyrics, val source: LyricsSource)

/**
 * Index of the line being sung at [positionMs], or -1 before the first line.
 * Uses binary search because it runs several times a second on long lists.
 */
fun List<LyricLine>.currentLineIndex(positionMs: Long): Int {
    var low = 0
    var high = lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) / 2
        if (this[mid].timeMs <= positionMs) {
            result = mid      // this line has started; maybe a later one has too
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}
