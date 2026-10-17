package io.github.nicholas_sb.restart.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nicholas_sb.restart.data.Song
import io.github.nicholas_sb.restart.data.db.SongStatsEntity
import io.github.nicholas_sb.restart.data.discover.ForYouRepository
import io.github.nicholas_sb.restart.data.streamedSong
import io.github.nicholas_sb.restart.downloader.PlaylistSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the For You page shows. [problem] explains an empty list ("Needs internet"). */
data class ForYouState(
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val problem: String? = null,
    val day: Long = -1,
)

/** Keeps the day's For You list: from the saved file, or freshly made from YouTube Music. */
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ForYouRepository(application)

    private val _forYou = MutableStateFlow(ForYouState())
    val forYou: StateFlow<ForYouState> = _forYou.asStateFlow()

    /**
     * Makes sure today's list is there. [newPicks] makes a new one even if there is (the
     * "New Picks" row). Needs the library (to skip songs you have) and stats (to pick seeds).
     */
    fun load(library: List<Song>, stats: Map<String, SongStatsEntity>, newPicks: Boolean = false) {
        val today = LocalDate.now().toEpochDay()
        val current = _forYou.value
        if (current.loading || library.isEmpty()) return
        if (!newPicks && current.day == today && current.songs.isNotEmpty()) return

        _forYou.update { it.copy(loading = true, problem = null) }
        viewModelScope.launch {
            val saved = if (newPicks) null else repository.load()
            val list = try {
                saved?.takeIf { it.day == today } ?: repository.make(today, library, stats)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _forYou.value = when {
                list != null -> ForYouState(list.songs.map(::toSong), day = list.day)
                // Offline: yesterday's picks still stream once the internet's back; better than nothing.
                saved != null -> ForYouState(saved.songs.map(::toSong), day = saved.day, problem = "Needs internet for new picks")
                current.songs.isNotEmpty() -> current.copy(loading = false, problem = "Needs internet for new picks")
                else -> ForYouState(problem = if (repository.hasInternet()) "Couldn't reach YouTube Music" else "Needs internet")
            }
        }
    }

    private fun toSong(song: PlaylistSong) =
        streamedSong(song.videoId, song.title, song.artist, (song.durationSec ?: 0) * 1000L)
}
