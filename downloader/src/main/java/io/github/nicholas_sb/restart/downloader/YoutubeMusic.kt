package io.github.nicholas_sb.restart.downloader

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * YouTube Music without downloading, through yt-dlp: a song's radio ("Start radio"), finding a
 * song on YouTube, and the address to stream a song's audio from.
 */
class YoutubeMusic(context: Context) {

    private val downloader = SongDownloader(context.applicationContext)

    /** YouTube Music's radio for a song: up to [size] songs like it, starting with the song itself. */
    suspend fun radio(videoId: String, size: Int): List<PlaylistSong> {
        require(isVideoId(videoId))
        downloader.prepare {}
        // "RDAMVM" + id is the radio YouTube Music plays when you press "Start radio".
        val link = PlaylistLink(listId = "RDAMVM$videoId", videoId = videoId, isYoutubeMusic = true)
        return downloader.readPlaylist(link, maxSongs = size).songs
    }

    /** The YouTube id of the best match for a song, or null (for songs whose file has no id). */
    suspend fun search(title: String, artist: String?): String? {
        downloader.prepare {}
        return withContext(Dispatchers.IO) {
            val query = URLEncoder.encode(listOfNotNull(artist, title).joinToString(" "), "UTF-8")
            // YouTube Music's search, "Songs" results only: the real track, not a lyrics video.
            val request = YoutubeDLRequest("https://music.youtube.com/search?q=$query#songs").apply {
                addOption("--flat-playlist")
                addOption("--playlist-items", "1")
                addOption("-J")
            }
            val out = YoutubeDL.getInstance().execute(request).out
            val entries = JSONObject(out.lines().last { it.trim().startsWith("{") }).optJSONArray("entries")
            entries?.optJSONObject(0)?.optString("id")?.takeIf(::isVideoId)
        }
    }

    /**
     * The YouTube Music song that best matches [title] by [artist], with its real title, artist
     * and length, or null. Looks at the first few "Songs" results and takes the one whose title
     * matches (YouTube Music often adds an English title: "あのバンド - That band"), else the
     * first. (Asking yt-dlp for "result 1" in full doesn't always give the first one listed.)
     */
    suspend fun findSong(title: String, artist: String?): PlaylistSong? {
        downloader.prepare {}
        val best = withContext(Dispatchers.IO) {
            val query = URLEncoder.encode(listOfNotNull(artist, title).joinToString(" "), "UTF-8")
            val request = YoutubeDLRequest("https://music.youtube.com/search?q=$query#songs").apply {
                addOption("--flat-playlist")
                addOption("--playlist-items", "1-5")
                addOption("-J")
            }
            val entries = lastJson(YoutubeDL.getInstance().execute(request).out).optJSONArray("entries")
            val results = (0 until (entries?.length() ?: 0)).mapNotNull { index ->
                val entry = entries!!.getJSONObject(index)
                val id = entry.optString("id").takeIf(::isVideoId) ?: return@mapNotNull null
                id to entry.optString("title")
            }
            val wanted = simplify(title)
            results.firstOrNull { (_, resultTitle) ->
                simplify(resultTitle) == wanted || simplify(resultTitle.substringBefore(" - ")) == wanted
            } ?: results.firstOrNull()
        } ?: return null
        return songDetails(best.first)
    }

    /** What YouTube knows about a video as a song (its real title and artist), or null if it isn't linked to one. */
    suspend fun songDetails(videoId: String): PlaylistSong? {
        require(isVideoId(videoId))
        downloader.prepare {}
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest("https://music.youtube.com/watch?v=$videoId").apply {
                addOption("-J")
                addOption("--skip-download")
            }
            lastJson(YoutubeDL.getInstance().execute(request).out).toSong()
        }
    }

    /** yt-dlp's JSON line (warnings may come before it). */
    private fun lastJson(out: String) = JSONObject(out.lines().last { it.trim().startsWith("{") })

    /** A video's details as a song, if YouTube has the real track and artist for it. */
    private fun JSONObject.toSong(): PlaylistSong? {
        val id = optString("id").takeIf(::isVideoId) ?: return null
        val track = optString("track").takeIf { has("track") && !isNull("track") && it.isNotBlank() } ?: return null
        val artist = optString("artist").takeIf { has("artist") && !isNull("artist") && it.isNotBlank() } ?: return null
        val duration = if (has("duration") && !isNull("duration")) getDouble("duration").toInt() else null
        // "YOASOBI, Ayase" -> "YOASOBI", like the downloads do.
        return PlaylistSong(id, track, artist.substringBefore(",").trim(), duration, clean = true)
    }

    /**
     * A link to stream [videoId]'s audio. YouTube's links stop working after some hours,
     * so each is kept for 3 hours and then looked up again.
     */
    suspend fun audioUrl(videoId: String): String {
        require(isVideoId(videoId))
        streamLinks[videoId]?.let { (url, foundAt) ->
            if (System.currentTimeMillis() - foundAt < LINK_LIFETIME_MS) return url
        }
        downloader.prepare {}
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest("https://music.youtube.com/watch?v=$videoId").apply {
                addOption("-f", "bestaudio[ext=m4a]/bestaudio")  // the same AAC audio we download
                addOption("-g")  // just print the media's address
            }
            val url = YoutubeDL.getInstance().execute(request).out.lines().first { it.startsWith("https://") }
            streamLinks[videoId] = url to System.currentTimeMillis()
            url
        }
    }

    companion object {
        private const val LINK_LIFETIME_MS = 3 * 60 * 60 * 1000L

        // Shared by everyone (the player asks from its own thread), hence a thread-safe map.
        private val streamLinks = ConcurrentHashMap<String, Pair<String, Long>>()

        /** Where a song's cover is on YouTube, biggest first (not every size exists for every video). */
        fun coverUrls(videoId: String): List<String> =
            listOf("maxresdefault", "hq720", "sddefault", "hqdefault").map { "https://i.ytimg.com/vi/$videoId/$it.jpg" }
    }
}
