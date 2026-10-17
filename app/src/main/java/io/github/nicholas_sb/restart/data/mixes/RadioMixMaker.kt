package io.github.nicholas_sb.restart.data.mixes

import io.github.nicholas_sb.restart.downloader.PlaylistSong
import io.github.nicholas_sb.restart.downloader.SongFixer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.streamedSong
import io.github.nicholas_sb.restart.downloader.LibrarySong
import io.github.nicholas_sb.restart.downloader.YoutubeMusic
import io.github.nicholas_sb.restart.downloader.findInLibrary

/**
 * A mix: its songs (after the first), and the streamed ones whose titles are still guesses
 * (by song id), for fixLater.
 */
class RadioMix(val songs: List<Song>, val guessed: Map<Long, PlaylistSong>)

/**
 * "Start Mix", like YouTube Music's "Start radio": the song's radio from YouTube Music.
 * Songs you already have play from the phone; the others are streamed.
 */
class RadioMixMaker(private val context: Context) {

    private val youtube = YoutubeMusic(context)
    private val fixer = SongFixer.get(context)

    /** The rest of the mix after [seed]. Throws if YouTube can't be reached. */
    suspend fun make(seed: Song, library: List<Song>): RadioMix {
        // Without internet yt-dlp keeps retrying for minutes: don't even start.
        check(hasInternet()) { "No internet" }
        val videoId = videoIdFor(seed) ?: error("Couldn't find this song on YouTube")

        val onPhone = library.map { LibrarySong(it.fileName, it.title, it.artist, it.durationMs) }
        val guessed = mutableMapOf<Long, PlaylistSong>()
        val songs = youtube.radio(videoId, MIX_SIZE)
            .filter { it.videoId != videoId }  // the radio starts with the song itself
            .map { radioSong ->
                // Fixed before (instant)? Use that. Still a guess? Fix it later.
                val known = fixer.cached(radioSong) ?: radioSong
                toSong(known, onPhone, library).also { if (!known.clean && it.isStreamed) guessed[it.id] = known }
            }
            .filter { it.id != seed.id }
            .distinctBy { it.id }
        return RadioMix(songs, guessed.filterKeys { id -> songs.any { it.id == id } })
    }

    /**
     * Looks up the mix's guessed songs (three at a time) while it plays, calling [onFixed]
     * with each song's id and what it really is: the official track, or your own copy.
     */
    suspend fun fixLater(mix: RadioMix, library: List<Song>, onFixed: (oldSongId: Long, fixed: Song) -> Unit) = coroutineScope {
        val onPhone = library.map { LibrarySong(it.fileName, it.title, it.artist, it.durationMs) }
        val gate = Semaphore(3)
        mix.guessed.forEach { (songId, guess) ->
            launch {
                val fixed = gate.withPermit { fixer.fix(guess) }
                if (fixed != guess) onFixed(songId, toSong(fixed, onPhone, library))
            }
        }
    }

    /** Your own copy if you have the song, else the streamed one. */
    private fun toSong(song: PlaylistSong, onPhone: List<LibrarySong>, library: List<Song>): Song =
        findInLibrary(onPhone, song)?.let { fileName -> library.firstOrNull { it.fileName == fileName } }
            ?: streamedSong(song.videoId, song.title, song.artist, (song.durationSec ?: 0) * 1000L)

    /** The song's YouTube id: from the file name if it's there, else YouTube Music's search. */
    suspend fun videoIdFor(song: Song): String? =
        song.streamVideoId
            ?: youtubeIdInFileName(song.fileName)
            ?: youtube.search(song.title, song.artist.takeUnless { it == "Unknown artist" })

    fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun youtubeIdInFileName(fileName: String): String? = idInFileName.find(fileName)?.groupValues?.get(1)

    private companion object {
        const val MIX_SIZE = 40

        /** yt-dlp names files "Title [id].m4a". */
        val idInFileName = Regex("""\[([A-Za-z0-9_-]{11})]\.[^.]+$""")
    }
}
