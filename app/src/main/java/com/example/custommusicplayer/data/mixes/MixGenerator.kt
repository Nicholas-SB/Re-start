package com.example.custommusicplayer.data.mixes

import com.example.custommusicplayer.data.db.SongStatsEntity
import com.example.custommusicplayer.playback.fisherYatesShuffle
import kotlin.math.pow
import kotlin.random.Random

/** A generated playlist. Songs are identified by file name, like everywhere stats are used. */
data class Mix(
    val id: String,
    val title: String,
    val description: String,
    val songFileNames: List<String>,
)

/**
 * Builds mixes from your library and listening habits. Pure Kotlin (no Android), so it's
 * easy to test and to tweak: each mix is one small function below.
 */
object MixGenerator {

    private const val DAY_MS = 24 * 60 * 60 * 1000L
    private const val FORGOTTEN_AFTER_MS = 30 * DAY_MS
    private const val FAVORITE_MIN_PLAYS = 3

    /**
     * @param songFileNames every song in the library
     * @param stats lifetime stats per song (songs never played may be missing)
     * @param playsLast30Days how often each song was played in the last 30 days
     * @param random pass Random(today) so mixes stay the same for the whole day
     */
    fun generate(
        songFileNames: List<String>,
        stats: Map<String, SongStatsEntity>,
        playsLast30Days: Map<String, Int>,
        now: Long,
        random: Random,
        mixSize: Int = 25,
    ): List<Mix> =
        listOf(
            dailyMix(songFileNames, stats, random, mixSize),
            onRepeat(songFileNames, playsLast30Days, mixSize),
            forgottenFavorites(songFileNames, stats, now, mixSize),
            freshPicks(songFileNames, stats, random, mixSize),
        ).filter { it.songFileNames.isNotEmpty() }

    /**
     * A bit of everything, leaning towards what you like. Each song gets a weight:
     * plays make it heavier, skips make it lighter, and unplayed songs still get a chance.
     *
     * Weighted picking without repeats uses the Efraimidis–Spirakis trick: give every song
     * the score random^(1/weight) and keep the highest scores. A heavier song's score is
     * pushed towards 1, so it's more likely to make the cut, but nothing is guaranteed.
     */
    fun dailyMix(songs: List<String>, stats: Map<String, SongStatsEntity>, random: Random, size: Int): Mix {
        fun weight(song: String): Double {
            val s = stats[song]
            val plays = s?.playCount ?: 0
            val skips = s?.skipCount ?: 0
            return (1.0 + plays) / (1.0 + 2.0 * skips)
        }
        val picked = songs
            .map { song -> song to random.nextDouble().pow(1.0 / weight(song)) }
            .sortedByDescending { (_, score) -> score }
            .take(size)
            .map { (song, _) -> song }
        return Mix("daily", "Daily Mix", "Your favorites and a few surprises. New every day.", picked)
    }

    /** What you've been playing most this month. */
    fun onRepeat(songs: List<String>, playsLast30Days: Map<String, Int>, size: Int): Mix {
        val inLibrary = songs.toSet()
        val picked = playsLast30Days
            .filterKeys { it in inLibrary }
            .entries
            .sortedByDescending { it.value }
            .take(size)
            .map { it.key }
        return Mix("on_repeat", "On Repeat", "Your most played songs in the last 30 days.", picked)
    }

    /** Songs you used to play a lot but haven't heard in a month. */
    fun forgottenFavorites(songs: List<String>, stats: Map<String, SongStatsEntity>, now: Long, size: Int): Mix {
        val picked = songs
            .mapNotNull { stats[it] }
            .filter { it.playCount >= FAVORITE_MIN_PLAYS }
            .filter { val last = it.lastPlayedAt; last != null && now - last > FORGOTTEN_AFTER_MS }
            .sortedByDescending { it.playCount }
            .take(size)
            .map { it.songFileName }
        return Mix("forgotten", "Forgotten Favorites", "Songs you loved but haven't played in a while.", picked)
    }

    /** Songs in your library you've never listened to all the way. */
    fun freshPicks(songs: List<String>, stats: Map<String, SongStatsEntity>, random: Random, size: Int): Mix {
        val unplayed = songs.filter { (stats[it]?.playCount ?: 0) == 0 }.toMutableList()
        fisherYatesShuffle(unplayed, random)
        return Mix("fresh", "Fresh Picks", "Songs you haven't really listened to yet.", unplayed.take(size))
    }
}
