package com.example.custommusicplayer.data.lyrics

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.inspector.MetadataRetriever
import com.example.custommusicplayer.data.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Finds a song's lyrics:
 *  1. a .lrc file with the same name next to the song ("Song.m4a" -> "Song.lrc", or
 *     "Song.en.lrc" as yt-dlp names them), else
 *  2. lyrics embedded in the song file's tags.
 *
 * No extra permission is needed for .lrc files: Android treats them as subtitle files, which
 * apps with the music permission may read. (Android 10 is covered by
 * requestLegacyExternalStorage in the manifest.)
 */
class LyricsLoader(private val context: Context) {

    suspend fun load(song: Song): LoadedLyrics? =
        loadLrcFile(song)?.let { LoadedLyrics(it, LyricsSource.LRC_FILE) }
            ?: loadEmbedded(song)?.let { LoadedLyrics(it, LyricsSource.EMBEDDED) }

    private suspend fun loadLrcFile(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val songFile = File(song.filePath ?: return@withContext null)
        val folder = songFile.parentFile ?: return@withContext null
        val baseName = songFile.nameWithoutExtension  // "Song I am. [Kohn9PRanoY]"
        try {
            // Exact name first, then names with a language code in between ("Song.en.lrc").
            val lrcFile = File(folder, "$baseName.lrc").takeIf { it.isFile }
                ?: folder.listFiles { file ->
                    file.name.startsWith("$baseName.") && file.name.endsWith(".lrc", ignoreCase = true)
                }?.minByOrNull { it.name }
            lrcFile?.let { LrcParser.parse(it.readText()) }
        } catch (e: Exception) {
            null  // unreadable; fall back to embedded lyrics
        }
    }

    /**
     * Reads the song's tags with Media3's MetadataRetriever (it parses the file the same way
     * the player does, without playing it) and looks for a lyrics tag.
     * Embedded lyrics can themselves be in LRC format, so they also go through LrcParser.
     */
    @OptIn(UnstableApi::class)
    private suspend fun loadEmbedded(song: Song): Lyrics? = try {
        MetadataRetriever.Builder(context, MediaItem.fromUri(song.uri)).build().use { retriever ->
            val trackGroups = retriever.retrieveTrackGroups().await()
            // Look through every track's metadata entries for the first lyrics tag.
            (0 until trackGroups.length)
                .map { trackGroups[it] }
                .flatMap { group -> (0 until group.length).map { group.getFormat(it) } }
                .mapNotNull { format -> format.metadata?.let(::findLyricsText) }
                .firstOrNull()
                ?.let(LrcParser::parse)
        }
    } catch (e: Exception) {
        null
    }

    /** Lyrics live in different tags depending on the file format. */
    @OptIn(UnstableApi::class)
    private fun findLyricsText(metadata: Metadata): String? {
        for (i in 0 until metadata.length()) {
            val text = when (val entry = metadata[i]) {
                // .m4a ("©lyr" atom) and ID3 lyrics frames come through as "USLT".
                is TextInformationFrame ->
                    if (entry.id == "USLT" || entry.description.equals("LYRICS", ignoreCase = true)) entry.values.firstOrNull() else null
                // .flac / .ogg / .opus
                is VorbisComment ->
                    if (entry.key.equals("LYRICS", ignoreCase = true) || entry.key.equals("UNSYNCEDLYRICS", ignoreCase = true)) entry.value else null
                else -> null
            }
            if (!text.isNullOrBlank()) return text
        }
        return null
    }
}

/**
 * Lets a coroutine wait for a Guava ListenableFuture (what Media3 returns) without blocking.
 * Same idea as MusicScanner.indexNewFiles(): wrap a callback in suspendCancellableCoroutine.
 */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            try {
                continuation.resume(get())
            } catch (e: ExecutionException) {
                continuation.resumeWithException(e.cause ?: e)
            }
        },
        Runnable::run,  // run the callback on whichever thread completes the future
    )
    continuation.invokeOnCancellation { cancel(false) }
}
