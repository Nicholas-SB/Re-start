package io.github.nicholas_sb.restart.downloader

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Turns messy videos from a radio ("Malcolm Todd - Earrings (Lyrics)" on some lyrics channel)
 * into the real song, by asking YouTube Music:
 *
 *  1. Search its songs for the guessed title and artist. Same title, same artist and about
 *     the same length: switch to that official track (proper title, cover and audio).
 *     Same title and artist but a different length (a music video with an intro, say):
 *     keep the video, take the proper title and artist.
 *  2. No match: ask what YouTube knows about the video itself as a song.
 *  3. Nothing: keep the guess.
 *
 * Every answer is remembered (in memory and in a file), so a song is only looked up once.
 */
class SongFixer private constructor(context: Context) {

    private val youtube = YoutubeMusic(context)
    private val file = File(context.filesDir, "song_fixes.json")

    /** Video id -> what it turned out to be (song = null: nothing better found). */
    private data class Fix(val song: PlaylistSong?)

    // Read from several coroutines at once, hence a thread-safe map.
    private val fixes: ConcurrentHashMap<String, Fix> by lazy { load() }
    private val saving = Mutex()

    /** The fixed song if this one was looked up before (instant), or null if it never was. */
    fun cached(song: PlaylistSong): PlaylistSong? {
        if (song.clean) return song
        val fix = fixes[song.videoId] ?: return null
        return fix.song ?: song
    }

    /** The real song for [song] (looked up if needed), or [song] itself if there's nothing better. */
    suspend fun fix(song: PlaylistSong): PlaylistSong {
        cached(song)?.let { return it }
        // Clean the guess again first: lists saved by an older version still have titles like
        // "YOASOBI「アイドル」 Official Music Video" that today's rules tidy up.
        val reparsed = TrackInfoParser.parse(null, null, null, song.title, song.artist, song.durationSec)
        val guess = song.copy(title = reparsed.title, artist = reparsed.artist.ifBlank { song.artist })
        val found = try {
            lookUp(guess)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return song  // no internet, YouTube hiccup: try again another time (not remembered)
        }
        // Nothing better on YouTube Music: at least keep the tidier guess.
        val result = found ?: guess.takeIf { it != song }
        fixes[song.videoId] = Fix(result)
        save()
        return result ?: song
    }

    private suspend fun lookUp(song: PlaylistSong): PlaylistSong? {
        youtube.findSong(song.title, song.artist)?.let { candidate ->
            when (compareSongs(song, candidate)) {
                SongMatch.SAME_SONG -> return candidate
                SongMatch.SAME_NAME -> return song.copy(title = candidate.title, artist = candidate.artist, clean = true)
                SongMatch.DIFFERENT -> {}
            }
        }
        return youtube.songDetails(song.videoId)?.let { details ->
            song.copy(title = details.title, artist = details.artist, clean = true)
        }
    }

    private fun load(): ConcurrentHashMap<String, Fix> {
        val map = ConcurrentHashMap<String, Fix>()
        try {
            val json = JSONObject(file.readText())
            json.keys().forEach { videoId ->
                val saved = json.optJSONObject(videoId)
                map[videoId] = Fix(
                    saved?.let {
                        PlaylistSong(
                            videoId = it.getString("videoId"),
                            title = it.getString("title"),
                            artist = it.optString("artist").ifEmpty { null },
                            durationSec = it.optInt("durationSec", 0).takeIf { seconds -> seconds > 0 },
                            clean = true,
                        )
                    }
                )
            }
        } catch (e: Exception) {
            // No file yet (or a broken one): start empty.
        }
        return map
    }

    private suspend fun save() = saving.withLock {
        withContext(Dispatchers.IO) {
            val json = JSONObject()
            fixes.forEach { (videoId, fix) ->
                json.put(
                    videoId,
                    fix.song?.let {
                        JSONObject()
                            .put("videoId", it.videoId)
                            .put("title", it.title)
                            .put("artist", it.artist.orEmpty())
                            .put("durationSec", it.durationSec ?: 0)
                    } ?: JSONObject.NULL
                )
            }
            file.writeText(json.toString())
        }
    }

    companion object {
        @Volatile
        private var instance: SongFixer? = null

        /** One fixer for the whole app, so For You and mixes share what's been looked up. */
        fun get(context: Context): SongFixer =
            instance ?: synchronized(this) {
                instance ?: SongFixer(context.applicationContext).also { instance = it }
            }
    }
}

enum class SongMatch { SAME_SONG, SAME_NAME, DIFFERENT }

/**
 * Is [candidate] (from YouTube Music) the song [guess] is a video of? Title and artist must
 * match (ignoring case, accents and punctuation; "YOASOBI" matches "YOASOBI, Ayase"). Lengths
 * within 15 s: the same recording. Further apart: the same song, but maybe a music video
 * with an intro, so only the names are trusted.
 */
fun compareSongs(guess: PlaylistSong, candidate: PlaylistSong): SongMatch {
    val guessArtist = simplify(guess.artist.orEmpty())
    val candidateArtist = simplify(candidate.artist.orEmpty())
    val sameTitle = simplify(guess.title) == simplify(candidate.title)
    val sameArtist = guessArtist.isNotEmpty() && candidateArtist.isNotEmpty() &&
        (guessArtist in candidateArtist || candidateArtist in guessArtist)
    if (!sameTitle || !sameArtist) return SongMatch.DIFFERENT

    val guessLength = guess.durationSec
    val candidateLength = candidate.durationSec
    val sameLength = guessLength == null || candidateLength == null || abs(guessLength - candidateLength) <= 15
    return if (sameLength) SongMatch.SAME_SONG else SongMatch.SAME_NAME
}

/** "Her's — Harvey!" -> "hers harvey": lower case, no accents, letters and digits only. */
internal fun simplify(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("['’]"), "")  // "her's" -> "hers", not "her s"
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
