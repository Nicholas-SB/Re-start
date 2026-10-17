package io.github.nicholas_sb.restart.data.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MonthlyHistoryTest {

    @Test
    fun monthsAreNewestFirstAndAddUpTheirDays() {
        val oct1 = LocalDate.of(2026, 10, 1).toEpochDay()
        val oct2 = LocalDate.of(2026, 10, 2).toEpochDay()
        val sep30 = LocalDate.of(2026, 9, 30).toEpochDay()
        val months = MonthlyHistory.months(
            monthPlays = mapOf("2026-09" to 3, "2026-10" to 5),
            listeningDays = mapOf(oct1 to 60_000L, oct2 to 30_000L, sep30 to 10_000L),
        )
        assertEquals(
            listOf(
                MonthSummary(YearMonth.of(2026, 10), 5, 90_000),
                MonthSummary(YearMonth.of(2026, 9), 3, 10_000),
            ),
            months,
        )
    }

    @Test
    fun aMonthWithOnlyListeningTimeStillShows() {
        val day = LocalDate.of(2026, 11, 3).toEpochDay()
        assertEquals(listOf(MonthSummary(YearMonth.of(2026, 11), 0, 5_000)), MonthlyHistory.months(emptyMap(), mapOf(day to 5_000L)))
    }

    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun detailCountsTopSongsArtistsAndTheBusiestDay() {
        val plays = listOf(
            Play("a.m4a", "Song A", "Roselia", at(1, 10)),
            Play("a.m4a", "Song A", "Roselia", at(2, 10)),
            Play("b.m4a", "Song B", "Roselia", at(2, 11)),
            Play("stream:xyz", "Streamed", "YOASOBI", at(2, 12)),
            Play("c.m4a", "Song C", null, at(3, 9)),
        )
        val detail = MonthlyHistory.detail(YearMonth.of(2026, 10), plays, 120_000, ZoneOffset.UTC)
        assertEquals(5, detail.plays)
        assertEquals(LocalDate.of(2026, 10, 2), detail.busiestDay)
        assertEquals(3, detail.busiestDayPlays)
        assertEquals(listOf("Song A", "Song B", "Song C", "Streamed"), detail.topSongs.map { it.title })
        assertEquals(2, detail.topSongs.first().plays)
        assertEquals(listOf("Roselia" to 3, "YOASOBI" to 1), detail.topArtists.map { it.title to it.plays })
    }
}
