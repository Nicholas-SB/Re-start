package io.github.nicholas_sb.restart.downloader

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

/**
 * Everything since the queue was last idle, for the notification. [failedTitles] are the
 * songs that couldn't be downloaded; [active] turns false when all the work is done.
 */
data class BatchProgress(
    val active: Boolean = false,
    val total: Int = 0,
    val finished: Int = 0,
    val failedTitles: List<String> = emptyList(),
    val currentStep: String? = null,  // e.g. the song being downloaded
    val currentPercent: Float = 0f,
)

/**
 * Runs downloads and playlist imports, one song at a time.
 *
 * There's one queue for the whole app (not one per screen), so downloads carry on when you
 * leave the app. While it has work, DownloadService keeps the app alive and shows the progress
 * in a notification.
 */
class DownloadQueue private constructor(private val context: Context) {

    private val downloader = SongDownloader(context)
    private val lrclib = LrclibClient()
    private val youtube = YoutubeMusic(context)

    // Our own scope instead of viewModelScope: it lives as long as the app's process.
    // SupervisorJob: one failed download doesn't cancel the others.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    private val _progress = MutableStateFlow(BatchProgress())
    val progress: StateFlow<BatchProgress> = _progress.asStateFlow()

    // A SharedFlow is a stream of one-off events (unlike StateFlow, which holds a current value).
    // The app listens to this to refresh the library when a song lands.
    private val _songDownloaded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val songDownloaded: SharedFlow<Unit> = _songDownloaded.asSharedFlow()

    // A Mutex lets only one coroutine in at a time: downloads queue up instead of running at once.
    private val oneAtATime = Mutex()
    private var nextId = 0
    private var runningTasks = 0

    /** The player's songs and playlists, for imports. The app sets this. */
    var library: MusicLibrary? = null

    fun download(url: String) {
        // Double-check here too: only real YouTube links ever reach yt-dlp.
        if (linkKind(url) != LinkKind.YOUTUBE || isWholePlaylist(url)) return
        val job = DownloadJob(id = nextId++, url = cleanLink(url))
        _jobs.update { listOf(job) + it }  // newest first
        launchTask {
            songsQueued(1)
            val fileName = oneAtATime.withLock { run(job) }
            songFinished(title = jobTitle(job.id), failed = fileName == null)
        }
    }

    /**
     * Imports a YouTube / YouTube Music playlist: lists its songs, reuses the ones already on
     * the phone, downloads the rest one by one, and adds each to [target] as soon as it's
     * ready, so the playlist keeps YouTube's order. Songs already in the playlist aren't
     * added again.
     */
    fun importPlaylist(text: String, target: ImportTarget) {
        val link = playlistLink(text) ?: return
        val importJob = DownloadJob(id = nextId++, url = link.url, title = "Playlist")
        _jobs.update { listOf(importJob) + it }
        launchTask {
            try {
                val library = library ?: error("Playlists aren't available")
                val playlist = oneAtATime.withLock {
                    downloader.prepare { message -> setStatus(importJob.id, DownloadStatus.Preparing(message)) }
                    setStatus(importJob.id, DownloadStatus.Preparing("Reading playlist…"))
                    _progress.update { it.copy(currentStep = "Reading playlist…") }
                    downloader.readPlaylist(link)
                }
                if (playlist.songs.isEmpty()) error("No songs found in that playlist")

                // Kotlin can return two values as a Pair and unpack them: val (a, b) = ...
                val (playlistId, playlistName) = when (target) {
                    is ImportTarget.ExistingPlaylist -> target.playlistId to target.name
                    is ImportTarget.NewPlaylist -> {
                        val name = target.name.trim().ifBlank { playlist.title }
                        library.createPlaylist(name) to name
                    }
                }

                // One card per song, listed right under the playlist's card, in order.
                val songJobs = playlist.songs.map { song ->
                    DownloadJob(
                        id = nextId++,
                        url = link.songUrl(song.videoId),
                        title = song.title,
                        playlistName = playlistName,
                        artist = song.artist,
                        playlistId = playlistId,
                        importId = importJob.id,
                    )
                }
                _jobs.update { jobs ->
                    val at = jobs.indexOfFirst { it.id == importJob.id } + 1
                    jobs.take(at) + songJobs + jobs.drop(at)
                }
                updateJob(importJob.id) { it.copy(title = "Playlist: ${playlist.title}") }
                songsQueued(songJobs.size)

                val onPhone = library.songs().toMutableList()
                var result = DownloadStatus.PlaylistProgress(playlistName, total = songJobs.size)
                setStatus(importJob.id, result)

                // zip pairs each song with its card: [(song1, job1), (song2, job2), ...]
                for ((song, job) in playlist.songs.zip(songJobs)) {
                    val existing = findInLibrary(onPhone, song)
                    val fileName = if (existing != null) {
                        setStatus(job.id, DownloadStatus.Done(alreadyInLibrary = true, lyrics = LyricsOutcome.NOT_CHECKED))
                        existing
                    } else {
                        oneAtATime.withLock { run(job) }?.also { downloaded ->
                            // Remember it, in case the playlist has the same song twice.
                            onPhone += LibrarySong(downloaded, song.title, song.artist.orEmpty(), (song.durationSec ?: 0) * 1000L)
                        }
                    }

                    result = when {
                        fileName == null -> result.copy(failedTitles = result.failedTitles + song.title)
                        else -> {
                            val added = library.addSong(playlistId, fileName)
                            result.copy(
                                downloaded = result.downloaded + if (existing == null) 1 else 0,
                                reused = result.reused + if (existing != null) 1 else 0,
                                added = result.added + if (added) 1 else 0,
                                alreadyInPlaylist = result.alreadyInPlaylist + if (added) 0 else 1,
                            )
                        }
                    }
                    result = result.copy(finished = result.finished + 1)
                    setStatus(importJob.id, result)
                    songFinished(song.title, failed = fileName == null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = friendlyMessage(e)
                setStatus(importJob.id, DownloadStatus.Failed(message))
                _progress.update { it.copy(failedTitles = it.failedTitles + "Playlist: $message") }
            }
        }
    }

    /**
     * For a song that failed (often "Video unavailable": removed, private or blocked where you
     * are): looks for the same song on YouTube Music, by title and artist, and downloads that
     * one instead, into the same playlist. Only a match with the same title and artist is taken,
     * so it never swaps in a different song.
     */
    fun findSameSong(jobId: Int) {
        val job = _jobs.value.firstOrNull { it.id == jobId } ?: return
        val title = job.title ?: return
        if (job.status !is DownloadStatus.Failed) return
        val failedVideoId = videoIdIn.find(job.url)?.groupValues?.get(1)
        launchTask {
            songsQueued(1)
            setStatus(job.id, DownloadStatus.Preparing("Looking for the same song…"))
            _progress.update { it.copy(currentStep = "Looking for $title…") }
            val found = try {
                youtube.findSong(title, job.artist)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val guess = PlaylistSong(failedVideoId.orEmpty(), title, job.artist)
            val match = found?.takeIf { it.videoId != failedVideoId && compareSongs(guess, it) != SongMatch.DIFFERENT }
            if (match == null) {
                val why = when {
                    job.artist == null -> "Couldn't search: the artist isn't known"
                    found == null -> "Not found on YouTube Music"
                    else -> "YouTube Music only has a different song (${found.title} · ${found.artist})"
                }
                setStatus(job.id, DownloadStatus.Failed(why))
                songFinished(title, failed = true)
                return@launchTask
            }

            val replacement = job.copy(url = "https://music.youtube.com/watch?v=${match.videoId}", title = match.title)
            updateJob(job.id) { replacement }
            val fileName = oneAtATime.withLock { run(replacement) }
            val playlistId = job.playlistId
            if (fileName != null && playlistId != null) {
                val added = library?.addSong(playlistId, fileName) == true
                job.importId?.let { importId -> countRecovered(importId, title, added) }
            }
            songFinished(title, failed = fileName == null)
        }
    }

    /** Takes a failed song off the list (it stays failed; it just isn't shown any more). */
    fun removeJob(jobId: Int) {
        _jobs.update { jobs -> jobs.filterNot { it.id == jobId && it.status is DownloadStatus.Failed } }
    }

    /** A playlist's failed song was found after all: one fewer failed on the playlist's card. */
    private fun countRecovered(importId: Int, title: String, added: Boolean) = updateJob(importId) { card ->
        val progress = card.status as? DownloadStatus.PlaylistProgress ?: return@updateJob card
        val failed = progress.failedTitles.toMutableList().apply { remove(title) }
        card.copy(
            status = progress.copy(
                failedTitles = failed,
                downloaded = progress.downloaded + 1,
                added = progress.added + if (added) 1 else 0,
                alreadyInPlaylist = progress.alreadyInPlaylist + if (added) 0 else 1,
            )
        )
    }

    /** Runs [block] as part of the current batch, starting a new batch (and the service) if idle. */
    private fun launchTask(block: suspend () -> Unit) {
        if (runningTasks == 0) {
            _progress.value = BatchProgress(active = true)
            DownloadService.start(context)
        }
        runningTasks++
        scope.launch {
            try {
                block()
            } finally {
                runningTasks--
                if (runningTasks == 0) _progress.update { it.copy(active = false, currentStep = null) }
            }
        }
    }

    private fun songsQueued(count: Int) = _progress.update { it.copy(total = it.total + count) }

    private fun songFinished(title: String, failed: Boolean) = _progress.update {
        it.copy(finished = it.finished + 1, failedTitles = if (failed) it.failedTitles + title else it.failedTitles)
    }

    private fun jobTitle(id: Int): String = _jobs.value.first { it.id == id }.let { it.title ?: it.url }

    /** Downloads one song and looks for its lyrics. Returns its file name, or null if it failed. */
    private suspend fun run(job: DownloadJob): String? {
        try {
            _progress.update { it.copy(currentStep = job.title ?: "Downloading…", currentPercent = 0f) }
            downloader.prepare { message -> setStatus(job.id, DownloadStatus.Preparing(message)) }
            setStatus(job.id, DownloadStatus.Downloading(0f))
            val song = retryIfRefused {
                downloader.download(job.url) { percent ->
                    setStatus(job.id, DownloadStatus.Downloading(percent))
                    _progress.update { it.copy(currentPercent = percent) }
                }
            }

            val title = "${song.track.artist} – ${song.track.title}"
            updateJob(job.id) { it.copy(title = title, status = DownloadStatus.FindingLyrics) }
            _progress.update { it.copy(currentStep = title) }

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
            throw e
        } catch (e: Exception) {
            setStatus(job.id, DownloadStatus.Failed(friendlyMessage(e)))
            return null
        }
    }

    /** YouTube sometimes refuses a download once ("HTTP Error 403") and allows it right after: try twice. */
    private suspend fun <T> retryIfRefused(block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if ("HTTP Error 403" !in (e.message ?: "")) throw e
            delay(2_000)
            block()
        }

    /**
     * Saves lyrics next to the song. If Android won't allow a .lrc file in that folder, keep it
     * in the app's private "lyrics" folder instead, which the player also checks.
     */
    private fun saveLyrics(nextToSong: File, lyrics: String) {
        try {
            nextToSong.writeText(lyrics)
        } catch (e: Exception) {
            val privateFolder = File(context.filesDir, "lyrics").apply { mkdirs() }
            File(privateFolder, nextToSong.name).writeText(lyrics)
        }
    }

    private fun setStatus(id: Int, status: DownloadStatus) = updateJob(id) { it.copy(status = status) }

    private fun updateJob(id: Int, change: (DownloadJob) -> DownloadJob) {
        // update { } is thread-safe; yt-dlp reports progress from a background thread.
        _jobs.update { jobs -> jobs.map { if (it.id == id) change(it) else it } }
    }

    /** yt-dlp errors are long; show the line that actually says what went wrong. */
    private fun friendlyMessage(e: Exception): String {
        val text = e.message ?: e.toString()
        if (connectionErrors.any { text.contains(it, ignoreCase = true) }) return "No internet connection"
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val error = lines.lastOrNull { "ERROR:" in it }?.substringAfter("ERROR:")?.trim()?.ifEmpty { null }
        return (error ?: lines.firstOrNull() ?: "Download failed").take(200)
    }

    companion object {
        /** The video id in a "watch?v=<id>" link. */
        private val videoIdIn = Regex("[?&]v=([A-Za-z0-9_-]{11})")

        /** How Python (inside yt-dlp) says the connection is gone. */
        private val connectionErrors = listOf(
            "No address associated with hostname", "connection abort", "Network is unreachable", "Connection reset", "timed out",
        )

        @Volatile
        private var instance: DownloadQueue? = null

        /** The one shared queue (created the first time it's asked for). */
        fun get(context: Context): DownloadQueue =
            instance ?: synchronized(this) {
                instance ?: DownloadQueue(context.applicationContext).also { instance = it }
            }
    }
}
