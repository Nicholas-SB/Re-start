package io.github.nicholas_sb.restart.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** One pasted link (or one song of an imported playlist) and how far along it is. */
data class DownloadJob(
    val id: Int,
    val url: String,
    val title: String? = null,  // known once the download finishes (or from the playlist)
    val status: DownloadStatus = DownloadStatus.Waiting,
    val playlistName: String? = null,  // for playlist imports: the playlist it goes into
)

sealed interface DownloadStatus {
    data object Waiting : DownloadStatus
    data class Preparing(val message: String) : DownloadStatus
    data class Downloading(val percent: Float) : DownloadStatus
    data object FindingLyrics : DownloadStatus
    data class Done(val alreadyInLibrary: Boolean, val lyrics: LyricsOutcome) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus

    /**
     * A playlist import's own card. Of the [finished] songs so far: [downloaded] were new,
     * [reused] were already on the phone, and [failedTitles] couldn't be downloaded.
     * [added] went into the playlist; [alreadyInPlaylist] were in it before.
     */
    data class PlaylistProgress(
        val playlistName: String,
        val total: Int,
        val finished: Int = 0,
        val downloaded: Int = 0,
        val reused: Int = 0,
        val added: Int = 0,
        val alreadyInPlaylist: Int = 0,
        val failedTitles: List<String> = emptyList(),
    ) : DownloadStatus
}

enum class LyricsOutcome { SAVED, ALREADY_THERE, NOT_FOUND, NOT_CHECKED }

/**
 * What the download screens use. The work itself happens in the app-wide DownloadQueue,
 * so it keeps going when you leave the screen (or the app).
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val queue = DownloadQueue.get(application)

    val jobs: StateFlow<List<DownloadJob>> = queue.jobs
    val songDownloaded: SharedFlow<Unit> = queue.songDownloaded

    /** Lets playlist imports see the player's songs and playlists. */
    fun setLibrary(library: MusicLibrary) {
        queue.library = library
    }

    fun download(url: String) = queue.download(url)

    fun importPlaylist(text: String, target: ImportTarget) = queue.importPlaylist(text, target)
}

/**
 * The link without any spaces. Links never contain spaces, but some keyboards add one after
 * every "." or "?" ("music. youtube. com/playlist? list=...").
 */
fun cleanLink(text: String): String = text.filterNot { it.isWhitespace() }

/** What kind of link was pasted. */
enum class LinkKind { YOUTUBE, SPOTIFY, UNKNOWN }

private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

/**
 * Checks the pasted text is a real web link to YouTube (or Spotify).
 *
 * This matters for safety: the text is handed to yt-dlp, and yt-dlp treats anything starting
 * with "-" as an option, some of which can run commands. So we don't just look for
 * "youtube.com" somewhere in the text; we parse it as a URL and require http(s) and an exact
 * host. (A link that starts with "https://" can never be mistaken for an option.)
 */
fun linkKind(text: String): LinkKind {
    val uri = try {
        java.net.URI(cleanLink(text))  // throws on characters links can't contain
    } catch (e: java.net.URISyntaxException) {
        return LinkKind.UNKNOWN
    }
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return LinkKind.UNKNOWN
    return when (uri.host?.lowercase()) {
        in youtubeHosts -> LinkKind.YOUTUBE
        "open.spotify.com" -> LinkKind.SPOTIFY
        else -> LinkKind.UNKNOWN
    }
}
