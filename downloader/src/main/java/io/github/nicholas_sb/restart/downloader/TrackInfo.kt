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

    // The same words without brackets, at the end: "Song Official Music Video", "Song - Lyrics".
    private val trailingNoise = Regex(
        """\s*[-|:]?\s*(official\s+)?(music\s+|lyrics?\s+)?(video|audio|mv|m/v|visuali[sz]er|lyrics?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    // Hashtags: "Song #shorts #fyp"
    private val hashtags = Regex("""\s*#\S+""")

    // Japanese-style titles: Artist「Song」 or Artist『Song』
    private val quotedTitle = Regex("""^(.+?)\s*[「『](.+?)[」』]""")

    // "Official" in channel names: "Official Arctic Monkeys", "BLACKPINK Official", "X Official Channel"
    private val officialChannel = Regex("""(?i)^official\s+|\s*official(\s+channel)?$""")

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
            .replace(hashtags, "")
            .replace(" – ", " - ")  // en and em dashes count as dashes too
            .replace(" — ", " - ")
            .substringBefore(" | ")  // "Song | Live at ..." -> "Song"
            .replace(trailingNoise, "")
            .trim()

        // Artist「Song」, common for Japanese songs. Only when the name in front is the channel's
        // ("YOASOBI「アイドル」" on "YOASOBI and Echoes"): in "Renai Circulation「恋愛サーキュレーション」"
        // on a singer's own channel, the name in front is the song's English title.
        quotedTitle.find(cleaned)?.let { match ->
            val (artist, title) = match.destructured
            if (artist.isNotBlank() && (uploader == null || sameName(artist, uploader))) {
                return TrackInfo(title.trim(), artist.trim(), null, durationSec)
            }
        }

        // "Artist - Title" is the most common pattern for music videos.
        val dash = cleaned.indexOf(" - ")
        return if (dash > 0) {
            TrackInfo(cleaned.substring(dash + 3).trim(), cleaned.substring(0, dash).trim(), null, durationSec)
        } else {
            TrackInfo(cleaned, cleanChannelName(uploader), null, durationSec)
        }
    }

    /** One name contains the other once simplified ("YOASOBI" and "YOASOBI and Echoes"). */
    private fun sameName(a: String, b: String): Boolean {
        val first = simplify(a)
        val second = simplify(cleanChannelName(b))
        return first.isNotEmpty() && second.isNotEmpty() && (first in second || second in first)
    }

    /** "Roselia - Topic" / "RoseliaVEVO" / "Official Roselia" -> "Roselia" */
    fun cleanChannelName(uploader: String?): String =
        (uploader ?: "")
            .removeSuffix(" - Topic")
            .removeSuffix("VEVO")
            .replace(officialChannel, "")
            .trim()
}
