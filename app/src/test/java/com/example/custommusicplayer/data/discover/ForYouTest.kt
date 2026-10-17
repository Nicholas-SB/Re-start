package com.example.custommusicplayer.data.discover

import com.example.custommusicplayer.downloader.PlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ForYouTest {

    private val songs = listOf(
        "a1" to "Roselia", "a2" to "Roselia", "a3" to "Roselia",
        "b1" to "NewJeans", "c1" to "SONICSIN", "d1" to "A-Wall", "e1" to "yungatita",
    )

    @Test
    fun seedsComeFromTheMostPlayedWithDifferentArtists() {
        val plays = mapOf("a1" to 50, "a2" to 40, "a3" to 30, "b1" to 20, "c1" to 10)
        repeat(20) { day ->
            val seeds = ForYou.pickSeeds(songs, plays, Random(day))
            assertEquals(3, seeds.size)
            assertTrue(seeds.all { (plays[it] ?: 0) > 0 })  // only played songs, there are enough
            assertEquals(3, seeds.map { seed -> songs.first { it.first == seed }.second }.toSet().size)
        }
    }

    @Test
    fun seedsChangeFromDayToDay() {
        val plays = songs.associate { it.first to 5 }
        val picks = (1..10).map { ForYou.pickSeeds(songs, plays, Random(it)).toSet() }.toSet()
        assertTrue(picks.size > 1)
    }

    @Test
    fun withoutPlaysAnySongsWill() {
        val seeds = ForYou.pickSeeds(songs, emptyMap(), Random(1))
        assertEquals(3, seeds.size)
        assertEquals(3, seeds.toSet().size)
    }

    @Test
    fun aSmallLibraryGivesFewerSeeds() {
        assertEquals(listOf("only"), ForYou.pickSeeds(listOf("only" to "x"), emptyMap(), Random(1)))
    }

    private fun song(id: String) = PlaylistSong(id, "Song $id")

    @Test
    fun blendTakesTurnsAndSkipsOwnedSeedsAndRepeats() {
        val radios = listOf(
            listOf(song("seed1"), song("x1"), song("x2"), song("shared")),
            listOf(song("seed2"), song("y1"), song("shared"), song("y2")),
        )
        val blended = ForYou.blend(radios, isOwned = { it.videoId == "x2" }, seedIds = setOf("seed1", "seed2"), size = 10)
        assertEquals(listOf("x1", "y1", "shared", "y2"), blended.map { it.videoId })
    }

    @Test
    fun blendStopsAtTheSize() {
        val radios = listOf((1..50).map { song("s$it") })
        assertEquals(30, ForYou.blend(radios, { false }, emptySet()).size)
    }
}
