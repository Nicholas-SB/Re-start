package io.github.nicholas_sb.restart.data.history

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One month on the History page: how many plays, and how long you listened. */
data class MonthSummary(val month: YearMonth, val plays: Int, val listenedMs: Long)

/** One play, as the history needs it: which song (its stats key), its name, and when. */
data class Play(val key: String, val title: String, val artist: String?, val playedAt: Long)

/** A song or artist and how often it was played that month. */
data class Count(val key: String, val title: String, val artist: String?, val plays: Int)

/** Everything one month's page shows. */
data class MonthDetail(
    val month: YearMonth,
    val plays: Int,
    val listenedMs: Long,
    val busiestDay: LocalDate?,
    val busiestDayPlays: Int,
    val topSongs: List<Count>,
    val topArtists: List<Count>,
)

/** Turns recorded plays and listening days into the History pages. Pure Kotlin, so it's tested. */
object MonthlyHistory {

    /**
     * Every month with plays or listening, newest first. [monthPlays] comes from the database
     * ("2026-10" -> plays); [listeningDays] is days since 1970 -> ms listened that day.
     */
    fun months(monthPlays: Map<String, Int>, listeningDays: Map<Long, Long>): List<MonthSummary> {
        val listened = listeningDays.entries
            .groupBy({ YearMonth.from(LocalDate.ofEpochDay(it.key)) }, { it.value })
            .mapValues { it.value.sum() }
        val plays = monthPlays.mapKeys { YearMonth.parse(it.key) }
        return (plays.keys + listened.keys)
            .sortedDescending()
            .map { month -> MonthSummary(month, plays[month] ?: 0, listened[month] ?: 0) }
    }

    /** One month's details from its [plays]: the busiest day, top songs and top artists. */
    fun detail(month: YearMonth, plays: List<Play>, listenedMs: Long, zone: ZoneId, top: Int = 10): MonthDetail {
        val byDay = plays.groupingBy { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }.eachCount()
        val busiest = byDay.maxByOrNull { it.value }

        val topSongs = plays.groupBy { it.key }
            .map { (key, songPlays) -> Count(key, songPlays.last().title, songPlays.last().artist, songPlays.size) }
            .sortedWith(compareByDescending<Count> { it.plays }.thenBy { it.title.lowercase() })
            .take(top)
        val topArtists = plays.mapNotNull { play -> play.artist?.takeIf { it.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .map { (artist, count) -> Count(artist, artist, null, count) }
            .sortedWith(compareByDescending<Count> { it.plays }.thenBy { it.title.lowercase() })
            .take(5)

        return MonthDetail(month, plays.size, listenedMs, busiest?.key, busiest?.value ?: 0, topSongs, topArtists)
    }
}
