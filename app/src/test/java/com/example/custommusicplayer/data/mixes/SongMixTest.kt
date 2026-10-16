package com.example.custommusicplayer.data.mixes

import com.example.custommusicplayer.data.db.SongStatsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SongMixTest {

    // 40 songs by other artists, plus a few that relate to the seed in different ways.
    private val artists = (1..40).associate { "other$it" to "Artist $it" } +
        mapOf(
            "seed" to "Roselia",
            "same artist" to "Roselia",
            "same playlist" to "Someone",
            "played around" to "Someone else",
        )

    private fun mix(random: Random, stats: Map<String, SongStatsEntity> = emptyMap(), size: Int = 10) =
        SongMix.build(
            seed = "seed",
            artists = artists,
            samePlaylist = mapOf("same playlist" to 1),
            playedAround = mapOf("played around" to 4),
            stats = stats,
            random = random,
            size = size,
        )

    @Test
    fun startsWithTheSeedAndHasNoRepeats() {
        val songs = mix(Random(1))
        assertEquals("seed", songs.first())
        assertEquals(10, songs.size)
        assertEquals(songs.size, songs.toSet().size)
    }

    @Test
    fun relatedSongsUsuallyMakeTheCut() {
        // Over many mixes, each related song is in far more of them than an unrelated one.
        val runs = (1..200).map { mix(Random(it)) }
        fun share(song: String) = runs.count { song in it } / runs.size.toDouble()
        val shares = listOf("same playlist", "same artist", "played around", "other1").associateWith(::share)
        assertTrue(shares.toString(), shares.getValue("same playlist") > 0.8)
        assertTrue(shares.toString(), shares.getValue("same artist") > 0.7)
        assertTrue(shares.toString(), shares.getValue("played around") > 0.7)
        assertTrue(shares.toString(), shares.getValue("other1") < 0.4)
    }

    @Test
    fun oftenSkippedSongsShowUpLess() {
        val stats = mapOf("other1" to SongStatsEntity("other1", playCount = 0, skipCount = 10))
        val runs = (1..200).map { mix(Random(it), stats) }
        val skipped = runs.count { "other1" in it }
        val normal = runs.count { "other2" in it }
        assertTrue("skipped $skipped, normal $normal", skipped < normal)
    }

    @Test
    fun aSmallLibraryGivesAShortMix() {
        val songs = SongMix.build("a", mapOf("a" to "x", "b" to "y"), emptyMap(), emptyMap(), emptyMap(), Random(0))
        assertEquals(listOf("a", "b"), songs)
    }
}
