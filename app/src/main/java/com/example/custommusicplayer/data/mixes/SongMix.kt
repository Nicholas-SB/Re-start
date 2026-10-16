package com.example.custommusicplayer.data.mixes

import com.example.custommusicplayer.data.db.SongStatsEntity
import kotlin.math.pow
import kotlin.random.Random

/**
 * A mix that starts from one song, like YouTube Music's "Start radio", but made only from your
 * own library. Pure Kotlin (no Android), so it's easy to test and to tweak.
 *
 * Each other song gets a weight from how it relates to the first one:
 *  - it's in the same playlist(s): the strongest hint that you think they go together
 *  - it's by the same artist
 *  - you often play it around the same time (within half an hour)
 * and from how much you like it (plays vs skips). Unrelated songs keep a small chance, so the
 * mix still has a few surprises.
 */
object SongMix {

    /** How far apart two plays can be to count as "played around the same time". */
    const val PLAYED_AROUND_WINDOW_MS = 30 * 60 * 1000L

    /**
     * @param seed the song the mix starts with
     * @param artists every song in the library (file name) and its artist
     * @param samePlaylist for each song: in how many of the seed's playlists it is
     * @param playedAround for each song: how often it was played near a play of the seed
     * @return the mix as file names, the seed first
     */
    fun build(
        seed: String,
        artists: Map<String, String>,
        samePlaylist: Map<String, Int>,
        playedAround: Map<String, Int>,
        stats: Map<String, SongStatsEntity>,
        random: Random,
        size: Int = 25,
    ): List<String> {
        val seedArtist = artists[seed]

        fun weight(song: String): Double {
            val related = 1.0 +
                20.0 * (samePlaylist[song] ?: 0).coerceAtMost(3) +
                12.0 * (if (artists[song] == seedArtist) 1 else 0) +
                6.0 * (playedAround[song] ?: 0).coerceAtMost(5)
            val s = stats[song]
            val liking = ((1.0 + (s?.playCount ?: 0)) / (1.0 + 2.0 * (s?.skipCount ?: 0))).coerceIn(0.2, 3.0)
            return related * liking
        }

        // Weighted picking without repeats (Efraimidis–Spirakis): every song gets the score
        // random^(1/weight), and the highest scores win. A heavier song's score is pushed
        // towards 1, so related songs tend to come first, but the order is never fixed.
        val rest = artists.keys
            .filter { it != seed }
            .map { song -> song to random.nextDouble().pow(1.0 / weight(song)) }
            .sortedByDescending { (_, score) -> score }
            .take(size - 1)
            .map { (song, _) -> song }
        return listOf(seed) + rest
    }
}
