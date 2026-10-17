package io.github.nicholas_sb.restart.ui

import io.github.nicholas_sb.restart.data.history.MonthDetail
import io.github.nicholas_sb.restart.data.history.MonthSummary
import io.github.nicholas_sb.restart.data.history.MonthlyHistory
import io.github.nicholas_sb.restart.data.history.Play
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.nicholas_sb.restart.data.db.MusicDatabase
import io.github.nicholas_sb.restart.data.db.SongStatsEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Reads the listening stats that PlaybackService writes. Because these are Room Flows,
 * the Home screen updates by itself whenever a play or skip is recorded.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = MusicDatabase.getInstance(application).statsDao()

    /** Lifetime stats, looked up by song file name. */
    val stats: StateFlow<Map<String, SongStatsEntity>> = dao.observeAllStats()
        .map { list -> list.associateBy { it.songFileName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Every month with listening, newest first (the History page). */
    val months: StateFlow<List<MonthSummary>> =
        combine(dao.observeMonthPlays(), dao.observeListeningDays()) { plays, days ->
            MonthlyHistory.months(plays.associate { it.month to it.plays }, days.associate { it.day to it.listenedMs })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _monthDetail = MutableStateFlow<MonthDetail?>(null)
    val monthDetail: StateFlow<MonthDetail?> = _monthDetail.asStateFlow()

    /**
     * Loads one month's page. [nameOf] names a song from your library by its file name;
     * streamed songs carry their own name, and deleted ones fall back to their file name.
     */
    fun loadMonth(month: YearMonth, nameOf: (fileName: String) -> Pair<String, String?>?) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val until = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val plays = dao.playsBetween(from, until).map { event ->
                val (title, artist) = event.title?.let { it to event.artist }
                    ?: nameOf(event.songFileName)
                    ?: (fileNameToTitle(event.songFileName) to null)
                Play(event.songFileName, title, artist, event.playedAt)
            }
            val listened = months.value.firstOrNull { it.month == month }?.listenedMs ?: 0
            _monthDetail.value = MonthlyHistory.detail(month, plays, listened, zone)
        }
    }

    /** "Song I am. [Kohn9PRanoY].m4a" -> "Song I am." (for songs that are gone from the phone). */
    private fun fileNameToTitle(fileName: String) = fileName.substringBeforeLast('.').replace(Regex(" \\[[A-Za-z0-9_-]{11}]$"), "")
}
