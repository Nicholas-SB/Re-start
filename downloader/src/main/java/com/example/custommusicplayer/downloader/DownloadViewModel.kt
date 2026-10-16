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

/** One pasted link and how far along it is. */
data class DownloadJob(
    val id: Int,
    val url: String,
    val title: String? = null,  // known once the download finishes
    val status: DownloadStatus = DownloadStatus.Waiting,
)

sealed interface DownloadStatus {
    data object Waiting : DownloadStatus
    data class Preparing(val message: String) : DownloadStatus
    data class Downloading(val percent: Float) : DownloadStatus
    data object FindingLyrics : DownloadStatus
    data class Done(val alreadyInLibrary: Boolean, val lyrics: LyricsOutcome) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}

enum class LyricsOutcome { SAVED, ALREADY_THERE, NOT_FOUND }

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

    fun download(url: String) {
        val job = DownloadJob(id = nextId++, url = url.trim())
        _jobs.update { listOf(job) + it }  // newest first
        viewModelScope.launch {
            oneAtATime.withLock { run(job) }
        }
    }

    private suspend fun run(job: DownloadJob) {
        try {
            downloader.prepare { message -> setStatus(job.id, DownloadStatus.Preparing(message)) }
            setStatus(job.id, DownloadStatus.Downloading(0f))
            val song = downloader.download(job.url) { percent -> setStatus(job.id, DownloadStatus.Downloading(percent)) }

            updateJob(job.id) { it.copy(title = "${song.track.artist} – ${song.track.title}", status = DownloadStatus.FindingLyrics) }

            // Never replace an .lrc that's already there (it may be one you picked yourself).
            val lyricsFile = downloader.lyricsFileFor(song.file)
            val lyricsOutcome = when {
                lyricsFile.exists() -> LyricsOutcome.ALREADY_THERE
                else -> lrclib.findLyrics(song.track)?.let { lyrics ->
                    lyricsFile.writeText(lyrics)
                    LyricsOutcome.SAVED
                } ?: LyricsOutcome.NOT_FOUND
            }

            val newFiles = buildList {
                if (!song.wasAlreadyInLibrary) add(song.file)
                if (lyricsOutcome == LyricsOutcome.SAVED) add(lyricsFile)
            }
            if (newFiles.isNotEmpty()) downloader.indexFiles(newFiles)

            setStatus(job.id, DownloadStatus.Done(song.wasAlreadyInLibrary, lyricsOutcome))
            _songDownloaded.tryEmit(Unit)
        } catch (e: CancellationException) {
            throw e  // the ViewModel is going away; let the coroutine stop
        } catch (e: Exception) {
            setStatus(job.id, DownloadStatus.Failed(friendlyMessage(e)))
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

fun linkKind(text: String): LinkKind {
    val link = text.trim().lowercase()
    return when {
        listOf("youtube.com/", "youtu.be/", "music.youtube.com/").any { it in link } -> LinkKind.YOUTUBE
        "open.spotify.com/" in link -> LinkKind.SPOTIFY
        else -> LinkKind.UNKNOWN
    }
}
