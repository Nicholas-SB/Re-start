package com.example.custommusicplayer.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

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

    /** A playlist import's own card: [finished] of [total] songs are through, [added] made it in. */
    data class PlaylistProgress(val playlistName: String, val finished: Int, val added: Int, val total: Int) : DownloadStatus
}

enum class LyricsOutcome { SAVED, ALREADY_THERE, NOT_FOUND, NOT_CHECKED }

/** Runs downloads one at a time and reports their progress. */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = SongDownloader(application)
    private val lrclib = LrclibClient()

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    // A SharedFlow is a stream of one-off events (unlike StateFlow, which holds a current value).
    // The app listens to this to refresh the library when a song lands.
    private val _songDownloaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val songDownloaded: SharedFlow<Unit> = _songDownloaded.asSharedFlow()

    // A Mutex lets only one coroutine in at a time: downloads queue up instead of running at once.
    private val oneAtATime = Mutex()
    private var nextId = 0

    /** Saves imported playlists. The app sets this; the downloader can't see the database. */
    var playlistWriter: PlaylistWriter? = null

    fun download(url: String) {
        // Double-check here too: only real YouTube links ever reach yt-dlp.
        if (linkKind(url) != LinkKind.YOUTUBE || isWholePlaylist(url)) return
        val job = DownloadJob(id = nextId++, url = url.trim())
        _jobs.update { listOf(job) + it }  // newest first
        viewModelScope.launch {
            oneAtATime.withLock { run(job) }
        }
    }

    /**
     * Imports a YouTube / YouTube Music playlist: lists its songs, then downloads them one by one
     * (songs already on the phone are reused) and adds each to [target] as soon as it's ready,
     * so the playlist keeps YouTube's order.
     */
    fun importPlaylist(text: String, target: ImportTarget) {
        val link = playlistLink(text) ?: return
        val importJob = DownloadJob(id = nextId++, url = link.url, title = "Playlist")
        _jobs.update { listOf(importJob) + it }
        viewModelScope.launch {
            try {
                val writer = playlistWriter ?: error("Playlists aren't available")
                val playlist = oneAtATime.withLock {
                    downloader.prepare { message -> setStatus(importJob.id, DownloadStatus.Preparing(message)) }
                    setStatus(importJob.id, DownloadStatus.Preparing("Reading playlist…"))
                    downloader.readPlaylist(link)
                }
                if (playlist.songs.isEmpty()) error("No songs found in that playlist")

                // Kotlin can return two values as a Pair and unpack them: val (a, b) = ...
                val (playlistId, playlistName) = when (target) {
                    is ImportTarget.ExistingPlaylist -> target.playlistId to target.name
                    is ImportTarget.NewPlaylist -> {
                        val name = target.name.trim().ifBlank { playlist.title }
                        writer.createPlaylist(name) to name
                    }
                }

                // One card per song, listed right under the playlist's card, in order.
                val songJobs = playlist.songs.map { song ->
                    DownloadJob(id = nextId++, url = link.songUrl(song.videoId), title = song.title, playlistName = playlistName)
                }
                _jobs.update { jobs ->
                    val at = jobs.indexOfFirst { it.id == importJob.id } + 1
                    jobs.take(at) + songJobs + jobs.drop(at)
                }
                updateJob(importJob.id) { it.copy(title = "Playlist: ${playlist.title}") }

                var added = 0
                // zip pairs each song with its card: [(song1, job1), (song2, job2), ...]
                playlist.songs.zip(songJobs).forEachIndexed { index, (song, job) ->
                    setStatus(importJob.id, DownloadStatus.PlaylistProgress(playlistName, index, added, songJobs.size))
                    val fileName = oneAtATime.withLock { run(job, videoId = song.videoId) }
                    if (fileName != null) {
                        writer.addSong(playlistId, fileName)
                        added++
                    }
                }
                setStatus(importJob.id, DownloadStatus.PlaylistProgress(playlistName, songJobs.size, added, songJobs.size))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus(importJob.id, DownloadStatus.Failed(friendlyMessage(e)))
            }
        }
    }

    /**
     * Downloads one song (or, if [videoId] is given and that song is already on the phone,
     * reuses it). Returns the song's file name, or null if it failed.
     */
    private suspend fun run(job: DownloadJob, videoId: String? = null): String? {
        try {
            videoId?.let { downloader.findInLibrary(it) }?.let { existing ->
                setStatus(job.id, DownloadStatus.Done(alreadyInLibrary = true, lyrics = LyricsOutcome.NOT_CHECKED))
                return existing
            }
            downloader.prepare { message -> setStatus(job.id, DownloadStatus.Preparing(message)) }
            setStatus(job.id, DownloadStatus.Downloading(0f))
            val song = downloader.download(job.url) { percent -> setStatus(job.id, DownloadStatus.Downloading(percent)) }

            updateJob(job.id) { it.copy(title = "${song.track.artist} – ${song.track.title}", status = DownloadStatus.FindingLyrics) }

            // Never replace an .lrc that's already there (it may be one you picked yourself).
            val lyricsFile = downloader.lyricsFileFor(song.file)
            val lyricsOutcome = when {
                lyricsFile.exists() -> LyricsOutcome.ALREADY_THERE
                else -> lrclib.findLyrics(song.track)?.let { lyrics ->
                    saveLyrics(lyricsFile, lyrics)
                    LyricsOutcome.SAVED
                } ?: LyricsOutcome.NOT_FOUND
            }

            val newFiles = buildList {
                if (!song.wasAlreadyInLibrary) add(song.file)
                if (lyricsOutcome == LyricsOutcome.SAVED && lyricsFile.exists()) add(lyricsFile)
            }
            if (newFiles.isNotEmpty()) downloader.indexFiles(newFiles)

            setStatus(job.id, DownloadStatus.Done(song.wasAlreadyInLibrary, lyricsOutcome))
            _songDownloaded.tryEmit(Unit)
            return song.file.name
        } catch (e: CancellationException) {
            throw e  // the ViewModel is going away; let the coroutine stop
        } catch (e: Exception) {
            setStatus(job.id, DownloadStatus.Failed(friendlyMessage(e)))
            return null
        }
    }

    /**
     * Saves lyrics next to the song. If Android won't allow a .lrc file in that folder, keep it
     * in the app's private "lyrics" folder instead, which the player also checks.
     */
    private fun saveLyrics(nextToSong: File, lyrics: String) {
        try {
            nextToSong.writeText(lyrics)
        } catch (e: Exception) {
            val privateFolder = File(getApplication<Application>().filesDir, "lyrics").apply { mkdirs() }
            File(privateFolder, nextToSong.name).writeText(lyrics)
        }
    }

    private fun setStatus(id: Int, status: DownloadStatus) = updateJob(id) { it.copy(status = status) }

    private fun updateJob(id: Int, change: (DownloadJob) -> DownloadJob) {
        // update { } is thread-safe; yt-dlp reports progress from a background thread.
        _jobs.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
    }

    /** yt-dlp errors are long; show the line that actually says what went wrong. */
    private fun friendlyMessage(e: Exception): String =
        (e.message ?: e.toString())
            .lines()
            .lastOrNull { it.contains("ERROR") }
            ?.substringAfter("ERROR:")
            ?.trim()
            ?.take(200)
            ?: (e.message ?: "Download failed").lines().first().take(200)
}

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
        java.net.URI(text.trim())  // throws on spaces and other characters links can't contain
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
