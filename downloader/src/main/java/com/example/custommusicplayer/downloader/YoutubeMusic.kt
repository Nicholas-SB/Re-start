package com.example.custommusicplayer.downloader

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
