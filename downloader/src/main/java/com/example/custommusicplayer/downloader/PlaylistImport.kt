package com.example.custommusicplayer.downloader

import java.net.URI

/**
 * How imported songs end up in a playlist. Playlists live in the player's database, which this
 * module can't see, so the app hands in an implementation (see DatabasePlaylistWriter).
 *
 * It's the same idea as a Java interface used to keep two parts apart: the downloader only
 * knows these two calls, not Room or the player.
 */
interface PlaylistWriter {
    /** Creates an empty playlist and returns its id. */
    suspend fun createPlaylist(name: String): Long

    /** Adds a song at the end of the playlist (skipped if it's already in it). */
    suspend fun addSong(playlistId: Long, songFileName: String)
}

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

data class PlaylistSong(val videoId: String, val title: String)

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
