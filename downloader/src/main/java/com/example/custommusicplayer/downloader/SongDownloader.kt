package com.example.custommusicplayer.downloader

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * A finished download: the song file in the music folder, its details for the lyrics search,
 * and whether that file was already there (then we keep the existing one).
 */
data class DownloadedSong(val file: File, val track: TrackInfo, val wasAlreadyInLibrary: Boolean)

/**
 * Runs yt-dlp on the phone with the same options as the PC command:
 *     yt-dlp -x --audio-format m4a --embed-thumbnail --embed-metadata <link>
 */
class SongDownloader(private val context: Context) {

    private val prefs = context.getSharedPreferences("downloader", Context.MODE_PRIVATE)
    private var initialized = false

    /**
     * Unpacks yt-dlp and ffmpeg the first time, and updates yt-dlp at most once a day.
     * YouTube changes often, and an old yt-dlp soon stops working.
     */
    suspend fun prepare(onStatus: (String) -> Unit) = withContext(Dispatchers.IO) {
        if (!initialized) {
            onStatus("Setting up downloader…")
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
        }
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
        if (System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS) {
            onStatus("Updating yt-dlp…")
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel._STABLE)
                prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            } catch (e: Exception) {
                // No internet or GitHub hiccup: try with the version we already have.
            }
        }
    }

    /** Downloads [url] into the music folder. [onProgress] gets 0–100. Throws on failure. */
    suspend fun download(url: String, onProgress: (Float) -> Unit): DownloadedSong = withContext(Dispatchers.IO) {
        // Work in the app's private cache, so half-finished files never show up in the library.
        val workDir = File(context.cacheDir, "downloads").apply {
            deleteRecursively()
            mkdirs()
        }

        val request = YoutubeDLRequest(url).apply {
            // Prefer YouTube's own AAC/m4a stream: it's copied as-is. Otherwise yt-dlp may pick
            // the Opus stream and re-encode it to m4a, which makes a ~3x bigger file for no gain.
            addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            addOption("-x")
            addOption("--audio-format", "m4a")
            addOption("--embed-thumbnail")
            addOption("--embed-metadata")
            addOption("--no-playlist")  // a song link from inside a playlist downloads just that song
            addOption("-o", "${workDir.absolutePath}/%(title)s [%(id)s].%(ext)s")
            // When done, print the details we need as one line of JSON.
            // (--print normally means "don't download"; --no-simulate turns downloading back on.)
            addOption("--print", "after_move:%(.{filepath,title,track,artist,album,uploader,duration})j")
            addOption("--no-simulate")
        }

        val response = YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress, _, _ ->
            onProgress(progress.coerceIn(0f, 100f))
        }

        val info = JSONObject(response.out.lines().last { it.trim().startsWith("{") })
        val downloaded = File(info.getString("filepath"))

        // Move it into the music folder. (A plain rename can't cross from app storage to
        // shared storage, so copy and then delete.) If the song is already there, keep that
        // copy: Android doesn't let us overwrite files that another app (or the PC) put there.
        val target = File(musicFolder(), downloaded.name)
        val wasAlreadyInLibrary = target.exists()
        if (!wasAlreadyInLibrary) downloaded.copyTo(target)
        downloaded.delete()

        DownloadedSong(
            file = target,
            track = TrackInfoParser.parse(
                track = info.stringOrNull("track"),
                artist = info.stringOrNull("artist"),
                album = info.stringOrNull("album"),
                videoTitle = info.stringOrNull("title") ?: target.nameWithoutExtension,
                uploader = info.stringOrNull("uploader"),
                durationSec = if (info.has("duration")) info.getDouble("duration").toInt() else null,
            ),
            wasAlreadyInLibrary = wasAlreadyInLibrary,
        )
    }

    /**
     * Lists a playlist's songs without downloading anything (--flat-playlist only reads the
     * playlist page, not each video). Skips videos that are private or deleted.
     */
    suspend fun readPlaylist(link: PlaylistLink): YoutubePlaylist = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(link.url).apply {
            addOption("--flat-playlist")
            addOption("--yes-playlist")
            addOption("-J")  // print the whole playlist as one JSON object
        }
        val response = YoutubeDL.getInstance().execute(request, PROCESS_ID)
        val info = JSONObject(response.out.lines().last { it.trim().startsWith("{") })
        val entries = info.optJSONArray("entries")
        val songs = (0 until (entries?.length() ?: 0)).mapNotNull { index ->
            val entry = entries!!.getJSONObject(index)
            val videoId = entry.optString("id")
            val title = entry.stringOrNull("title") ?: videoId
            PlaylistSong(videoId, title).takeIf { isVideoId(videoId) && title !in unavailableTitles }
        }
        // YouTube Music albums are called "Album - <name>" on YouTube; just "<name>" reads better.
        val title = info.stringOrNull("title")?.removePrefix("Album - ") ?: "YouTube playlist"
        YoutubePlaylist(title, songs)
    }

    /**
     * Finds a song that's already on the phone by its YouTube id, which yt-dlp puts in the
     * file name ("Title [id].m4a"). Returns its file name, or null.
     */
    fun findInLibrary(videoId: String): String? {
        val nameColumn = MediaStore.Audio.Media.DISPLAY_NAME
        // In LIKE, "_" means "any one character", so this can match a bit too much; the exact
        // check on each result below sorts that out.
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(nameColumn), "$nameColumn LIKE ?", arrayOf("%[$videoId].%"), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0) ?: continue
                if (name.contains("[$videoId].")) return name
            }
        }
        return null
    }

    /** "<song name>.lrc" next to the song, where the player looks for lyrics. */
    fun lyricsFileFor(song: File): File = File(song.parentFile, "${song.nameWithoutExtension}.lrc")

    /** Tells Android about the new files right away, so the library shows them. */
    suspend fun indexFiles(files: List<File>) {
        suspendCancellableCoroutine { continuation ->
            val remaining = AtomicInteger(files.size)
            MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null) { _, _ ->
                if (remaining.decrementAndGet() == 0) continuation.resume(Unit)
            }
        }
    }

    /**
     * Where songs are saved: Music/Re-start/. Android only lets apps create files in standard
     * folders like Music, so this can't be any folder you pick (e.g. YMusic). The player always
     * includes this folder in the library.
     */
    @Suppress("DEPRECATION")  // still the simplest way to get the folder's path
    private fun musicFolder(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Re-start").apply { mkdirs() }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null

    companion object {
        private const val PROCESS_ID = "song-download"
        private const val KEY_LAST_UPDATE = "ytdlp_last_update"
        private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L

        /** What YouTube shows instead of a title for videos you can't play. */
        private val unavailableTitles = setOf("[Private video]", "[Deleted video]")
    }
}
