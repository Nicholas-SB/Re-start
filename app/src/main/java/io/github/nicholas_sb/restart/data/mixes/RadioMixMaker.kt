package io.github.nicholas_sb.restart.data.mixes

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.streamedSong
import io.github.nicholas_sb.restart.downloader.LibrarySong
import io.github.nicholas_sb.restart.downloader.YoutubeMusic
import io.github.nicholas_sb.restart.downloader.findInLibrary

/**
 * "Start Mix", like YouTube Music's "Start radio": the song's radio from YouTube Music.
 * Songs you already have play from the phone; the others are streamed.
 */
class RadioMixMaker(private val context: Context) {

    private val youtube = YoutubeMusic(context)

    /** The rest of the mix after [seed]. Throws if YouTube can't be reached. */
    suspend fun make(seed: Song, library: List<Song>): List<Song> {
        // Without internet yt-dlp keeps retrying for minutes: don't even start.
        check(hasInternet()) { "No internet" }
        val videoId = videoIdFor(seed) ?: error("Couldn't find this song on YouTube")

        val onPhone = library.map { LibrarySong(it.fileName, it.title, it.artist, it.durationMs) }
        val byFileName = library.associateBy { it.fileName }
        return youtube.radio(videoId, MIX_SIZE)
            .filter { it.videoId != videoId }  // the radio starts with the song itself
            .map { radioSong ->
                findInLibrary(onPhone, radioSong)?.let(byFileName::get)
                    ?: streamedSong(radioSong.videoId, radioSong.title, radioSong.artist, (radioSong.durationSec ?: 0) * 1000L)
            }
            .filter { it.id != seed.id }
            .distinctBy { it.id }
    }

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
