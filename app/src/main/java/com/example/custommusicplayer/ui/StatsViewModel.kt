package com.example.custommusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.custommusicplayer.data.db.MusicDatabase
import com.example.custommusicplayer.data.db.SongStatsEntity
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

    /** Plays per song in the last 30 days (counted from when the app was opened). */
    val playsLast30Days: StateFlow<Map<String, Int>> = dao.observePlayCountsSince(System.currentTimeMillis() - THIRTY_DAYS_MS)
        .map { rows -> rows.associate { it.songFileName to it.plays } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        private const val THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000L
    }
}
