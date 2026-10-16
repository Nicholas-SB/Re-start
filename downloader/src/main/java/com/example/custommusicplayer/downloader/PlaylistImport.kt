package com.example.custommusicplayer.downloader

import java.net.URI
import kotlin.math.abs

/**
 * What the downloader needs from the player's library. Songs and playlists live in the app
 * module, which this module can't see, so the app hands in an implementation (AppMusicLibrary).
 *
 * It's the same idea as a Java interface used to keep two parts apart: the downloader only
 * knows these three calls, not Room or MediaStore.
 */
interface MusicLibrary {
    /** The songs in your library right now (what the player shows). */
    suspend fun songs(): List<LibrarySong>

    /** Creates an empty playlist and returns its id. */
    suspend fun createPlaylist(name: String): Long

    /** Adds a song at the end of the playlist. Returns false if it was already in it. */
    suspend fun addSong(playlistId: Long, songFileName: String): Boolean
}

/** A song already on the phone, as far as matching is concerned. */
data class LibrarySong(val fileName: String, val title: String, val artist: String, val durationMs: Long)

/** Where an imported playlist's songs go. */
sealed interface ImportTarget {
    /** A new playlist. A blank name means "use the name it has on YouTube". */
    data class NewPlaylist(val name: String) : ImportTarget
    data class ExistingPlaylist(val playlistId: Long, val name: String) : ImportTarget
}

/** One of your playlists, for the "Save to" menu. */
data class PlaylistChoice(val id: Long, val name: String)

/** A playlist as listed by yt-dlp: its name and its songs, in order. */
data class YoutubePlaylist(val title: String, val songs: List<PlaylistSong>)

/** One song of a YouTube playlist. [artist] is the channel ("X - Topic" becomes "X"). */
data class PlaylistSong(val videoId: String, val title: String, val artist: String? = null, val durationSec: Int? = null)

/**
 * Finds [wanted] among the songs already on the phone, so it isn't downloaded twice.
 * Returns the matching file's name, or null.
 *
 *  1. Songs downloaded with yt-dlp have the video id in their file name: "Title [id].m4a".
 *  2. Otherwise (e.g. songs from another app), the same title, plus the same artist or a
 *     length within 3 seconds. Title alone isn't enough: covers often share the name.
 */
fun findInLibrary(library: List<LibrarySong>, wanted: PlaylistSong): String? {
    library.firstOrNull { "[${wanted.videoId}]." in it.fileName }?.let { return it.fileName }

    val title = wanted.title.normalized()
    val artist = wanted.artist?.normalized()
    return library.firstOrNull { song ->
        val sameArtist = artist != null && song.artist.normalized() == artist
        val sameLength = wanted.durationSec != null && song.durationMs > 0 &&
            abs(song.durationMs / 1000 - wanted.durationSec) <= 3
        song.title.normalized() == title && (sameArtist || sameLength)
    }?.fileName
}

/** "  As  It Was " and "as it was" count as the same. */
private fun String.normalized() = lowercase().replace(whitespace, " ").trim()

private val whitespace = Regex("\\s+")

/**
 * A checked YouTube / YouTube Music playlist link, reduced to its ids. The links handed to
 * yt-dlp are rebuilt from these ids, never taken from the pasted text.
 */
data class PlaylistLink(val listId: String, val videoId: String?, val isYoutubeMusic: Boolean) {

    private val host get() = if (isYoutubeMusic) "music.youtube.com" else "www.youtube.com"

    /** A "watch" link with a list (e.g. a mix) only works in that form, so keep it. */
    val url: String
        get() = if (videoId != null) "https://$host/watch?v=$videoId&list=$listId" else "https://$host/playlist?list=$listId"

    fun songUrl(songVideoId: String) = "https://$host/watch?v=$songVideoId"
}

private val listIdPattern = Regex("[A-Za-z0-9_-]+")
private val videoIdPattern = Regex("[A-Za-z0-9_-]{11}")

fun isVideoId(text: String) = text.matches(videoIdPattern)

/** True for a link to a whole playlist (not a song that happens to be played from one). */
fun isWholePlaylist(text: String): Boolean = playlistLink(text)?.let { it.videoId == null } ?: false

/** The playlist in a pasted link, or null if it isn't a YouTube link with a playlist in it. */
fun playlistLink(text: String): PlaylistLink? {
    if (linkKind(text) != LinkKind.YOUTUBE) return null
    val uri = URI(text.trim())
    // The query is the part after "?", e.g. "list=PL123&si=abc". Split it into name/value pairs.
    val params = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
        pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { (name, value) -> name to value }
    }.toMap()
    val listId = params["list"]?.takeIf { it.matches(listIdPattern) } ?: return null
    return PlaylistLink(
        listId = listId,
        videoId = params["v"]?.takeIf(::isVideoId),
        isYoutubeMusic = uri.host.lowercase() == "music.youtube.com",
    )
}
