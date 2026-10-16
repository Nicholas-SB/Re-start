package com.example.custommusicplayer.data.mixes

import com.example.custommusicplayer.data.db.SongStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MixGeneratorTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1000 * day

    @Test
    fun onRepeatOrdersByRecentPlaysAndIgnoresDeletedSongs() {
        val mix = MixGenerator.onRepeat(
            songs = listOf("a", "b", "c"),
            playsLast30Days = mapOf("a" to 2, "b" to 9, "deleted" to 50),
            size = 25,
        )
        assertEquals(listOf("b", "a"), mix.songFileNames)
    }

    @Test
    fun forgottenFavoritesAreOftenPlayedButNotRecently() {
        val stats = listOf(
            SongStatsEntity("old favorite", playCount = 10, lastPlayedAt = now - 60 * day),
            SongStatsEntity("recent favorite", playCount = 10, lastPlayedAt = now - 2 * day),
            SongStatsEntity("played once", playCount = 1, lastPlayedAt = now - 60 * day),
        ).associateBy { it.songFileName }
        val mix = MixGenerator.forgottenFavorites(stats.keys.toList(), stats, now, size = 25)
        assertEquals(listOf("old favorite"), mix.songFileNames)
    }

    @Test
    fun freshPicksOnlyHasUnplayedSongs() {
        val stats = mapOf("played" to SongStatsEntity("played", playCount = 1))
        val mix = MixGenerator.freshPicks(listOf("played", "new1", "new2"), stats, Random(1), size = 25)
        assertEquals(setOf("new1", "new2"), mix.songFileNames.toSet())
    }

    @Test
    fun dailyMixHasNoDuplicatesAndRespectsTheSize() {
        val songs = (1..100).map { "song$it" }
        val mix = MixGenerator.dailyMix(songs, emptyMap(), Random(3), size = 25)
        assertEquals(25, mix.songFileNames.size)
        assertEquals(25, mix.songFileNames.toSet().size)
    }

    @Test
    fun dailyMixPrefersLovedSongsOverSkippedOnes() {
        val stats = mapOf(
            "loved" to SongStatsEntity("loved", playCount = 10),
            "skipped" to SongStatsEntity("skipped", skipCount = 10),
        )
        val random = Random(5)
        // Pick one song 1000 times: "loved" (weight 11) should win almost always vs "skipped" (weight 1/21).
        val lovedWins = (1..1000).count {
            MixGenerator.dailyMix(listOf("loved", "skipped"), stats, random, size = 1).songFileNames.single() == "loved"
        }
        assertTrue("loved won $lovedWins/1000", lovedWins > 950)
    }

    @Test
    fun sameDaySameMix() {
        val songs = (1..50).map { "song$it" }
        val first = MixGenerator.generate(songs, emptyMap(), emptyMap(), now, Random(42))
        val second = MixGenerator.generate(songs, emptyMap(), emptyMap(), now, Random(42))
        assertEquals(first, second)
    }

    @Test
    fun emptyMixesAreLeftOut() {
        // New library, nothing played: only Daily Mix and Fresh Picks make sense.
        val mixes = MixGenerator.generate(listOf("a", "b"), emptyMap(), emptyMap(), now, Random(1))
        assertEquals(listOf("daily", "fresh"), mixes.map { it.id })
    }
}
