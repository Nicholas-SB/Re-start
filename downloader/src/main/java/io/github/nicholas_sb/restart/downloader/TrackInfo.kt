package io.github.nicholas_sb.restart.downloader

/** The song details we use to search for lyrics. */
data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Int?,
)

/**
 * Works out a song's real title and artist from what yt-dlp tells us.
 *
 * YouTube Music links come with proper `track` and `artist` fields. Regular YouTube videos
 * only have a title like "Roselia - Song I am. (Official Music Video)" and a channel name,
 * so we clean those up.
 */
object TrackInfoParser {

    // Bracketed bits that describe the video rather than the song, e.g. "(Official Video)",
    // "[MV]", "【Lyrics】", "(HD Remastered)".
    private val videoNoise = Regex(
        """\s*[(\[【][^)\]】]*\b(official|video|audio|mv|m/v|lyrics?|visuali[sz]er|hd|4k|remaster(ed)?)\b[^)\]】]*[)\]】]""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(
        track: String?,
        artist: String?,
        album: String?,
        videoTitle: String,
        uploader: String?,
        durationSec: Int?,
    ): TrackInfo {
        // YouTube Music: trust its fields. "A, B" -> "A" (lyrics sites list the main artist).
        if (!track.isNullOrBlank() && !artist.isNullOrBlank()) {
            return TrackInfo(track.trim(), artist.substringBefore(",").trim(), album?.takeIf { it.isNotBlank() }, durationSec)
        }

        val cleaned = videoTitle
            .replace(videoNoise, "")
            .substringBefore(" | ")  // "Song | Live at ..." -> "Song"
            .trim()

        // "Artist - Title" is the most common pattern for music videos.
        val dash = cleaned.indexOf(" - ")
        return if (dash > 0) {
            TrackInfo(cleaned.substring(dash + 3).trim(), cleaned.substring(0, dash).trim(), null, durationSec)
        } else {
            TrackInfo(cleaned, cleanChannelName(uploader), null, durationSec)
        }
    }

    /** "Roselia - Topic" / "RoseliaVEVO" -> "Roselia" */
    private fun cleanChannelName(uploader: String?): String =
        (uploader ?: "")
            .removeSuffix(" - Topic")
            .removeSuffix("VEVO")
            .trim()
}
